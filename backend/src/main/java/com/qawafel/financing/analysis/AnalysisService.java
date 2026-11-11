package com.qawafel.financing.analysis;

import com.qawafel.financing.analysis.AnalysisResult.*;
import com.qawafel.financing.domain.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns cleaned invoices into the numbers a credit analyst looks at.
 * Pure calculation: no database access, so it's easy to test and to reason about.
 */
@Service
public class AnalysisService {

    /** Window of recent sales used for DSO. */
    static final int RECENT_WINDOW_DAYS = 90;
    /** Invoices due in this many days before the as-of date count as "recent" behaviour. */
    static final int SLOWDOWN_RECENT_DAYS = 120;
    /** Invoices due in the first this-many months of the period are the customer's baseline. */
    static final int SLOWDOWN_BASELINE_MONTHS = 6;
    /** Recent lateness must exceed the baseline by this many days (smaller swings are normal noise). */
    static final double SLOWDOWN_THRESHOLD_DAYS = 15;
    /** Minimum invoices on each side before comparing, so a couple of odd payments don't trigger a flag. */
    static final int SLOWDOWN_MIN_INVOICES = 5;
    static final double CHRONIC_LATE_DAYS = 30;
    /** A customer with no invoice in this many days, who bought early in the period, has stopped buying. */
    static final int STOPPED_BUYING_DAYS = 75;
    static final int NEW_CUSTOMER_AFTER_DAYS = 180;
    static final double LARGEST_CUSTOMER_FLAG_SHARE = 0.15;

    private final RiskScorer riskScorer;
    private final FinancingCalculator financingCalculator;

    public AnalysisService(RiskScorer riskScorer, FinancingCalculator financingCalculator) {
        this.riskScorer = riskScorer;
        this.financingCalculator = financingCalculator;
    }

    public AnalysisResult analyze(FinancingApplication app, List<Invoice> invoices, List<DataIssue> issues) {
        LocalDate asOf = app.getAsOfDate();
        List<Invoice> sales = invoices.stream().filter(i -> i.getType() == DocumentType.INVOICE).toList();

        List<MonthlyPoint> monthly = monthly(app.getPeriodStart(), invoices);
        double netRevenue = monthly.stream().mapToDouble(MonthlyPoint::netSales).sum();

        List<CustomerSummary> customers = customers(app, invoices, netRevenue);
        Concentration concentration = concentration(customers);
        CollectionMetrics collections = collections(sales, asOf);
        DataQuality quality = dataQuality(app, issues);

        double trend = revenueTrend(monthly);
        double volatility = coefficientOfVariation(monthly);
        double overdue = sales.stream()
                .filter(i -> i.getDueDate().isBefore(asOf))
                .mapToDouble(i -> d(i.outstanding())).sum();
        double open = sales.stream().mapToDouble(i -> d(i.outstanding())).sum();
        int active = (int) customers.stream().filter(c -> !c.flags().contains(CustomerFlag.STOPPED_BUYING)).count();

        Headline headline = new Headline(round(netRevenue), round(netRevenue / monthly.size()), round1(trend),
                active, sales.size(), round(open), round(overdue),
                collections.daysSalesOutstanding(), collections.averageDaysLate());

        RiskAssessment risk = riskScorer.score(new RiskScorer.Inputs(trend, volatility, concentration,
                collections, customers, quality));
        Double requested = app.getRequestedAmount() == null ? null : d(app.getRequestedAmount());
        FinancingEstimate financing = financingCalculator.estimate(sales, customers, asOf, requested, quality);

        return new AnalysisResult(toApplication(app), headline, monthly, concentration, collections,
                customers, risk, financing, quality);
    }

    // ------------------------------------------------------------------ revenue

    private List<MonthlyPoint> monthly(LocalDate periodStart, List<Invoice> invoices) {
        YearMonth first = YearMonth.from(periodStart);
        Map<YearMonth, List<Invoice>> byMonth = invoices.stream()
                .collect(Collectors.groupingBy(i -> YearMonth.from(i.getInvoiceDate())));
        List<MonthlyPoint> out = new ArrayList<>();
        for (int m = 0; m < 12; m++) {
            YearMonth ym = first.plusMonths(m);
            List<Invoice> rows = byMonth.getOrDefault(ym, List.of());
            double gross = rows.stream().filter(i -> i.getType() == DocumentType.INVOICE)
                    .mapToDouble(i -> d(i.getSubtotal())).sum();
            double credits = -rows.stream().filter(i -> i.getType() == DocumentType.CREDIT_NOTE)
                    .mapToDouble(i -> d(i.getSubtotal())).sum();
            int count = (int) rows.stream().filter(i -> i.getType() == DocumentType.INVOICE).count();
            int buyers = (int) rows.stream().filter(i -> i.getType() == DocumentType.INVOICE)
                    .map(Invoice::getCustomerKey).distinct().count();
            out.add(new MonthlyPoint(ym.toString(), round(gross), round(credits), round(gross - credits), count, buyers));
        }
        return out;
    }

    /**
     * Last 3 months vs first 3 months. With only 12 months we can't compare against last year,
     * so seasonality (e.g. Ramadan) can distort this; the chart and the conditions call that out.
     */
    static double revenueTrend(List<MonthlyPoint> monthly) {
        double first = monthly.subList(0, 3).stream().mapToDouble(MonthlyPoint::netSales).sum();
        double last = monthly.subList(monthly.size() - 3, monthly.size()).stream().mapToDouble(MonthlyPoint::netSales).sum();
        return first == 0 ? 0 : (last / first - 1) * 100;
    }

    static double coefficientOfVariation(List<MonthlyPoint> monthly) {
        double mean = monthly.stream().mapToDouble(MonthlyPoint::netSales).average().orElse(0);
        if (mean == 0) return 0;
        double var = monthly.stream().mapToDouble(p -> Math.pow(p.netSales() - mean, 2)).average().orElse(0);
        return Math.sqrt(var) / mean;
    }

    // ------------------------------------------------------------------ customers

    private List<CustomerSummary> customers(FinancingApplication app, List<Invoice> invoices, double netRevenue) {
        LocalDate asOf = app.getAsOfDate();
        LocalDate recentFrom = asOf.minusDays(SLOWDOWN_RECENT_DAYS);
        LocalDate baselineUntil = app.getPeriodStart().plusMonths(SLOWDOWN_BASELINE_MONTHS);
        Map<String, List<Invoice>> byCustomer = invoices.stream()
                .collect(Collectors.groupingBy(Invoice::getCustomerKey, LinkedHashMap::new, Collectors.toList()));

        List<CustomerSummary> out = new ArrayList<>();
        for (Map.Entry<String, List<Invoice>> e : byCustomer.entrySet()) {
            List<Invoice> all = e.getValue();
            List<Invoice> sales = all.stream().filter(i -> i.getType() == DocumentType.INVOICE).toList();
            if (sales.isEmpty()) continue;
            Invoice any = sales.get(0);

            double revenue = all.stream().mapToDouble(i -> d(i.getSubtotal())).sum();
            LocalDate firstInv = sales.stream().map(Invoice::getInvoiceDate).min(LocalDate::compareTo).orElseThrow();
            LocalDate lastInv = sales.stream().map(Invoice::getInvoiceDate).max(LocalDate::compareTo).orElseThrow();

            List<Invoice> paidReliable = sales.stream().filter(AnalysisService::measurablePayment).toList();
            Double avgLate = paidReliable.isEmpty() ? null : round1(paidReliable.stream()
                    .mapToLong(i -> ChronoUnit.DAYS.between(i.getDueDate(), i.getPaidDate())).average().orElse(0));

            // Compare recent behaviour with the customer's own baseline from the start of the period.
            // Recent lateness counts unpaid overdue invoices too (days overdue so far), otherwise a customer
            // who simply stopped paying would look *better* because their late invoices aren't paid yet.
            List<Long> recent = new ArrayList<>();
            List<Long> baseline = new ArrayList<>();
            for (Invoice i : sales) {
                LocalDate due = i.getDueDate();
                if (due.isAfter(asOf)) continue;
                if (due.isAfter(recentFrom)) {
                    if (measurablePayment(i)) {
                        recent.add(ChronoUnit.DAYS.between(due, i.getPaidDate()));
                    } else if (i.outstanding().signum() > 0) {
                        recent.add(ChronoUnit.DAYS.between(due, asOf));
                    }
                } else if (due.isBefore(baselineUntil) && measurablePayment(i)) {
                    baseline.add(ChronoUnit.DAYS.between(due, i.getPaidDate()));
                }
            }
            Double recentLate = recent.size() >= SLOWDOWN_MIN_INVOICES ? round1(avg(recent)) : null;
            Double earlierLate = baseline.size() >= SLOWDOWN_MIN_INVOICES ? round1(avg(baseline)) : null;

            double openBal = sales.stream().mapToDouble(i -> d(i.outstanding())).sum();
            double overdue60 = sales.stream()
                    .filter(i -> ChronoUnit.DAYS.between(i.getDueDate(), asOf) > 60)
                    .mapToDouble(i -> d(i.outstanding())).sum();

            List<CustomerFlag> flags = new ArrayList<>();
            if (recentLate != null && earlierLate != null && recentLate - earlierLate >= SLOWDOWN_THRESHOLD_DAYS) {
                flags.add(CustomerFlag.PAYING_SLOWER);
            }
            if (avgLate != null && avgLate > CHRONIC_LATE_DAYS && paidReliable.size() >= 5) {
                flags.add(CustomerFlag.CHRONIC_LATE_PAYER);
            }
            if (lastInv.isBefore(asOf.minusDays(STOPPED_BUYING_DAYS))
                    && firstInv.isBefore(app.getPeriodStart().plusDays(90))) {
                flags.add(CustomerFlag.STOPPED_BUYING);
            }
            if (firstInv.isAfter(app.getPeriodStart().plusDays(NEW_CUSTOMER_AFTER_DAYS))) {
                flags.add(CustomerFlag.NEW_CUSTOMER);
            }
            if (openBal > 0 && overdue60 / openBal > 0.3 && overdue60 > 10_000) {
                flags.add(CustomerFlag.HIGH_OVERDUE);
            }

            out.add(new CustomerSummary(e.getKey(), any.getCustomerName(), any.getCustomerSegment(), any.getCity(),
                    round(revenue), netRevenue == 0 ? 0 : round4(revenue / netRevenue), sales.size(),
                    firstInv, lastInv, avgLate, earlierLate, recentLate, round(openBal), round(overdue60), flags));
        }
        out.sort(Comparator.comparingDouble(CustomerSummary::revenue).reversed());

        if (!out.isEmpty() && out.get(0).share() > LARGEST_CUSTOMER_FLAG_SHARE) {
            CustomerSummary top = out.get(0);
            List<CustomerFlag> flags = new ArrayList<>(top.flags());
            flags.add(0, CustomerFlag.LARGEST_CUSTOMER);
            out.set(0, new CustomerSummary(top.customerKey(), top.name(), top.segment(), top.city(), top.revenue(),
                    top.share(), top.invoiceCount(), top.firstInvoice(), top.lastInvoice(), top.averageDaysLate(),
                    top.baselineDaysLate(), top.recentDaysLate(), top.openBalance(), top.overdue60Balance(), flags));
        }
        return out;
    }

    /** A paid invoice whose paid date we trust. */
    static boolean measurablePayment(Invoice i) {
        return i.getType() == DocumentType.INVOICE && i.getPaymentStatus() == PaymentStatus.PAID
                && i.isPaidDateReliable() && i.getPaidDate() != null;
    }

    private Concentration concentration(List<CustomerSummary> customers) {
        double top1 = customers.isEmpty() ? 0 : customers.get(0).share();
        double top5 = customers.stream().limit(5).mapToDouble(CustomerSummary::share).sum();
        double hhi = customers.stream().mapToDouble(c -> Math.pow(c.share() * 100, 2)).sum();
        List<CustomerShare> top = customers.stream().limit(5)
                .map(c -> new CustomerShare(c.customerKey(), c.name(), c.revenue(), c.share())).toList();
        return new Concentration(round4(top1), round4(top5), Math.round(hhi), top);
    }

    // ------------------------------------------------------------------ collections

    private CollectionMetrics collections(List<Invoice> sales, LocalDate asOf) {
        double open = sales.stream().mapToDouble(i -> d(i.outstanding())).sum();
        LocalDate from = asOf.minusDays(RECENT_WINDOW_DAYS);
        double recentSales = sales.stream()
                .filter(i -> i.getInvoiceDate().isAfter(from) && !i.getInvoiceDate().isAfter(asOf))
                .mapToDouble(i -> d(i.getTotal())).sum();
        // Days Sales Outstanding on the last 90 days of sales: how many days of sales are still unpaid.
        double dso = recentSales == 0 ? 0 : open / recentSales * RECENT_WINDOW_DAYS;

        List<Invoice> paid = sales.stream().filter(AnalysisService::measurablePayment).toList();
        double daysToPay = paid.stream()
                .mapToLong(i -> ChronoUnit.DAYS.between(i.getInvoiceDate(), i.getPaidDate())).average().orElse(0);
        double daysLate = paid.stream()
                .mapToLong(i -> ChronoUnit.DAYS.between(i.getDueDate(), i.getPaidDate())).average().orElse(0);
        double onTime = paid.isEmpty() ? 0 :
                100.0 * paid.stream().filter(i -> !i.getPaidDate().isAfter(i.getDueDate())).count() / paid.size();

        String[] labels = {"Not yet due", "1-30 days overdue", "31-60 days overdue", "61-90 days overdue", "90+ days overdue"};
        double[] amt = new double[5];
        int[] cnt = new int[5];
        for (Invoice i : sales) {
            double o = d(i.outstanding());
            if (o <= 0) continue;
            long late = ChronoUnit.DAYS.between(i.getDueDate(), asOf);
            int b = late <= 0 ? 0 : late <= 30 ? 1 : late <= 60 ? 2 : late <= 90 ? 3 : 4;
            amt[b] += o;
            cnt[b]++;
        }
        List<AgingBucket> aging = new ArrayList<>();
        for (int b = 0; b < 5; b++) {
            aging.add(new AgingBucket(labels[b], round(amt[b]), cnt[b], open == 0 ? 0 : round4(amt[b] / open)));
        }
        return new CollectionMetrics(round1(dso), round1(daysToPay), round1(daysLate), round1(onTime), paid.size(), aging);
    }

    // ------------------------------------------------------------------ data quality

    private DataQuality dataQuality(FinancingApplication app, List<DataIssue> issues) {
        Map<IssueType, Long> byType = issues.stream()
                .collect(Collectors.groupingBy(DataIssue::getType, () -> new EnumMap<>(IssueType.class), Collectors.counting()));
        Map<IssueType, IssueSeverity> severity = new EnumMap<>(IssueType.class);
        issues.forEach(i -> severity.merge(i.getType(), i.getSeverity(), (a, b) -> a.ordinal() <= b.ordinal() ? a : b));
        List<IssueCount> counts = byType.entrySet().stream()
                .map(e -> new IssueCount(e.getKey().name(), e.getKey().label(), severity.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(IssueCount::severity).thenComparing(IssueCount::count, Comparator.reverseOrder()))
                .toList();
        int warnings = (int) issues.stream().filter(i -> i.getSeverity() == IssueSeverity.WARNING).count();
        int info = (int) issues.stream().filter(i -> i.getSeverity() == IssueSeverity.INFO).count();
        return new DataQuality(app.getRowsRead(), app.getRowsAccepted(), app.getRowsExcluded(), warnings, info, counts);
    }

    private static Application toApplication(FinancingApplication a) {
        return new Application(a.getId(), a.getCompanyName(), a.getSourceFileName(), a.getUploadedAt(),
                a.getPeriodStart(), a.getPeriodEnd(), a.getAsOfDate(),
                a.getRequestedAmount() == null ? null : d(a.getRequestedAmount()),
                a.getStatus(), a.getDecision(), a.getDecisionNote(), a.getDecidedAt());
    }

    // ------------------------------------------------------------------ helpers

    static double d(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static double avg(List<Long> xs) {
        return xs.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    static double round4(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }
}

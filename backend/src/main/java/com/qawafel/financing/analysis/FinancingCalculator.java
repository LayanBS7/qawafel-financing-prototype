package com.qawafel.financing.analysis;

import com.qawafel.financing.analysis.AnalysisResult.*;
import com.qawafel.financing.domain.Invoice;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Suggests how much could be lent against the wholesaler's unpaid invoices, using a
 * "borrowing base", the standard method for receivables financing:
 *
 * <ol>
 *   <li>Start from everything customers owe today.</li>
 *   <li>Remove invoices more than 60 days past due: they may never be paid.</li>
 *   <li>Remove customers who are chronic late payers or have stopped buying.</li>
 *   <li>Cap any single customer at 25% of what remains, so one default can't sink the facility.</li>
 *   <li>Lend 80% of the result, leaving a buffer for disputes and credit notes.</li>
 * </ol>
 * Percentages are prototype assumptions; in practice the bank sets them.
 */
@Component
public class FinancingCalculator {

    static final int MAX_DAYS_PAST_DUE = 60;
    static final double SINGLE_CUSTOMER_CAP = 0.25;
    static final double ADVANCE_RATE = 0.80;
    static final double ROUND_TO = 10_000;

    public FinancingEstimate estimate(List<Invoice> sales, List<CustomerSummary> customers, LocalDate asOf,
                                      Double requested, DataQuality quality) {
        List<Invoice> open = sales.stream().filter(i -> i.outstanding().signum() > 0).toList();
        double totalOpen = sum(open);

        List<Invoice> aged = open.stream()
                .filter(i -> ChronoUnit.DAYS.between(i.getDueDate(), asOf) > MAX_DAYS_PAST_DUE).toList();
        List<Invoice> current = open.stream().filter(i -> !aged.contains(i)).toList();

        Map<String, CustomerSummary> byKey = customers.stream()
                .collect(Collectors.toMap(CustomerSummary::customerKey, c -> c));
        Set<String> ineligible = customers.stream()
                .filter(c -> c.flags().contains(CustomerFlag.CHRONIC_LATE_PAYER)
                        || c.flags().contains(CustomerFlag.STOPPED_BUYING))
                .map(CustomerSummary::customerKey)
                .collect(Collectors.toSet());
        double ineligibleAmt = sum(current.stream().filter(i -> ineligible.contains(i.getCustomerKey())).toList());

        Map<String, Double> pool = current.stream()
                .filter(i -> !ineligible.contains(i.getCustomerKey()))
                .collect(Collectors.groupingBy(Invoice::getCustomerKey, Collectors.summingDouble(i -> i.outstanding().doubleValue())));
        double poolTotal = pool.values().stream().mapToDouble(Double::doubleValue).sum();
        double cap = poolTotal * SINGLE_CUSTOMER_CAP;
        List<String> capped = new ArrayList<>();
        double excess = 0;
        for (Map.Entry<String, Double> e : pool.entrySet()) {
            if (e.getValue() > cap) {
                excess += e.getValue() - cap;
                capped.add(byKey.containsKey(e.getKey()) ? byKey.get(e.getKey()).name() : e.getKey());
            }
        }
        double eligible = poolTotal - excess;
        double limit = Math.floor(eligible * ADVANCE_RATE / ROUND_TO) * ROUND_TO;

        List<FinancingStep> steps = List.of(
                new FinancingStep("Open receivables", r(totalOpen),
                        open.size() + " unpaid invoices on " + asOf + " (incl. VAT)."),
                new FinancingStep("Less: more than " + MAX_DAYS_PAST_DUE + " days past due", -r(sum(aged)),
                        aged.size() + " invoices unlikely to be collected on time."),
                new FinancingStep("Less: ineligible customers", -r(ineligibleAmt),
                        ineligible.isEmpty() ? "None." : "Chronic late payers or customers who stopped buying: "
                                + names(ineligible, byKey) + "."),
                new FinancingStep("Less: concentration above " + Math.round(SINGLE_CUSTOMER_CAP * 100) + "% per customer",
                        -r(excess), capped.isEmpty() ? "No customer above the cap." : "Capped: " + String.join(", ", capped) + "."),
                new FinancingStep("Eligible receivables", r(eligible), "What the facility can be secured against."),
                new FinancingStep("Suggested limit (" + Math.round(ADVANCE_RATE * 100) + "% advance rate)", limit,
                        "Rounded down to the nearest SAR 10,000."));

        List<String> conditions = new ArrayList<>();
        if (!ineligible.isEmpty()) {
            conditions.add("Exclude invoices to " + names(ineligible, byKey) + " from the financed pool.");
        }
        if (!capped.isEmpty()) {
            conditions.add("Limit exposure to " + String.join(", ", capped) + " to "
                    + Math.round(SINGLE_CUSTOMER_CAP * 100) + "% of the pool.");
        }
        customers.stream().filter(c -> c.flags().contains(CustomerFlag.PAYING_SLOWER))
                .forEach(c -> conditions.add("Monitor " + c.name() + ": paying " + Math.round(c.recentDaysLate())
                        + " days late recently vs " + Math.round(c.baselineDaysLate()) + " at the start of the period."));
        if (quality.rowsExcluded() > 0 || quality.warnings() > 0) {
            conditions.add("Ask the wholesaler to confirm the " + quality.rowsExcluded() + " excluded rows and "
                    + quality.warnings() + " flagged rows before funding.");
        }
        conditions.add("Request the matching bank statements to confirm that paid invoices were actually received.");
        conditions.add("Request the previous year's sales to separate seasonality (Ramadan, summer) from real growth.");

        Double coverage = requested == null || requested == 0 ? null : Math.round(limit / requested * 1000) / 10.0;
        return new FinancingEstimate("Borrowing base on receivables", steps, r(eligible), ADVANCE_RATE, limit,
                requested, coverage, conditions);
    }

    private static double sum(List<Invoice> xs) {
        return xs.stream().mapToDouble(i -> i.outstanding().doubleValue()).sum();
    }

    private static String names(Set<String> keys, Map<String, CustomerSummary> byKey) {
        return keys.stream().map(k -> byKey.containsKey(k) ? byKey.get(k).name() : k).sorted()
                .collect(Collectors.joining(", "));
    }

    private static double r(double v) {
        return Math.round(v * 100) / 100.0;
    }
}

package com.qawafel.financing.ingest;

import com.qawafel.financing.domain.DocumentType;
import com.qawafel.financing.domain.IssueSeverity;
import com.qawafel.financing.domain.IssueType;
import com.qawafel.financing.domain.PaymentStatus;
import com.qawafel.financing.ingest.ValidationResult.CleanInvoice;
import com.qawafel.financing.ingest.ValidationResult.Issue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.qawafel.financing.domain.IssueSeverity.*;

/**
 * Business rules for an invoice file: what makes a row unusable, what can be corrected,
 * and what only needs flagging. Every decision is recorded as an {@link Issue} so the analyst
 * (and the bank) can see exactly what was changed and why.
 *
 * <p>Rule of thumb used throughout: <b>exclude</b> a row when we can't trust it at all
 * (no amount, no date, duplicate, wrong period); <b>keep and flag</b> it when the sale clearly
 * happened but a detail is off (VAT, total, paid date).
 */
@Component
public class InvoiceValidator {

    static final BigDecimal VAT_RATE = new BigDecimal("0.15");
    /** Rounding differences below this are ignored. */
    static final BigDecimal TOLERANCE = new BigDecimal("1.00");
    static final int DEFAULT_TERMS_DAYS = 30;
    /** Saudi VAT registration numbers: 15 digits, starting and ending with 3. */
    private static final Pattern SAUDI_VAT = Pattern.compile("^3\\d{13}3$");

    public ValidationResult validate(ParsedWorkbook wb) {
        Period period = determinePeriod(wb);
        List<Issue> issues = new ArrayList<>();
        List<CleanInvoice> clean = new ArrayList<>();
        Map<String, Integer> seenSignatures = new HashMap<>();
        Map<String, Integer> seenNumbers = new HashMap<>();

        for (RawInvoiceRow raw : wb.rows()) {
            CleanInvoice ok = validateRow(raw, period, seenSignatures, seenNumbers, issues);
            if (ok != null) clean.add(ok);
        }

        clean = unifyCustomerNames(clean, issues);
        issues.sort(Comparator.comparingInt(Issue::sourceRow));

        String company = wb.companyName() != null ? wb.companyName() : "Unnamed wholesaler";
        return new ValidationResult(company, period.start(), period.end(), period.asOf(),
                wb.rows().size(), clean, issues);
    }

    // ------------------------------------------------------------------ period

    record Period(LocalDate start, LocalDate end, LocalDate asOf) {
        boolean contains(LocalDate d) {
            return !d.isBefore(start) && !d.isAfter(end);
        }
    }

    /**
     * Uses the period from the "Company Info" sheet when present. Otherwise takes the 12 months
     * ending with the latest month that has a meaningful number of invoices, so one row with a
     * mistyped future year can't shift the whole window.
     */
    Period determinePeriod(ParsedWorkbook wb) {
        LocalDate start = wb.periodStart();
        LocalDate end = wb.periodEnd();
        if (start == null || end == null) {
            Map<YearMonth, Long> perMonth = wb.rows().stream()
                    .map(RawInvoiceRow::invoiceDate)
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(YearMonth::from, Collectors.counting()));
            if (perMonth.isEmpty()) {
                throw new InvalidFileException("No row has a readable invoice date, so the period can't be determined.");
            }
            long threshold = Math.max(1, wb.rows().size() / 100);
            YearMonth last = perMonth.entrySet().stream()
                    .filter(e -> e.getValue() >= threshold)
                    .map(Map.Entry::getKey)
                    .max(Comparator.naturalOrder())
                    .orElseThrow();
            end = last.atEndOfMonth();
            start = last.minusMonths(11).atDay(1);
        }
        LocalDate asOf = wb.exportedOn() != null ? wb.exportedOn() : end;
        return new Period(start, end, asOf);
    }

    // ------------------------------------------------------------------ one row

    private CleanInvoice validateRow(RawInvoiceRow r, Period period, Map<String, Integer> seenSignatures,
                                     Map<String, Integer> seenNumbers, List<Issue> issues) {
        int row = r.rowNumber();
        String no = r.invoiceNumber();

        // 1. Cells that had a value we couldn't read
        for (Map.Entry<Column, String> bad : r.unreadable().entrySet()) {
            boolean critical = bad.getKey().required();
            issues.add(new Issue(row, no, IssueType.INVALID_VALUE, critical ? EXCLUDED : WARNING,
                    "Couldn't read '" + bad.getValue() + "' in column " + bad.getKey() + ".",
                    critical ? "Row excluded." : "Value ignored."));
        }
        if (r.unreadable().keySet().stream().anyMatch(Column::required)) {
            return null;
        }

        // 2. Required fields
        List<String> missing = new ArrayList<>();
        if (no == null) missing.add("invoice number");
        if (r.invoiceDate() == null) missing.add("invoice date");
        if (r.customerName() == null && r.customerId() == null) missing.add("customer");
        if (r.subtotal() == null) missing.add("subtotal");
        if (!missing.isEmpty()) {
            issues.add(new Issue(row, no, IssueType.MISSING_REQUIRED_FIELD, EXCLUDED,
                    "Missing " + String.join(", ", missing) + ".",
                    "Row excluded: an invoice without these can't be counted."));
            return null;
        }

        // 3. Duplicates
        Integer firstRow = seenSignatures.putIfAbsent(r.contentSignature(), row);
        if (firstRow != null) {
            issues.add(new Issue(row, no, IssueType.DUPLICATE_ROW, EXCLUDED,
                    "Identical to row " + firstRow + ".",
                    "Duplicate removed so the sale is counted once."));
            return null;
        }
        Integer sameNumberRow = seenNumbers.putIfAbsent(no, row);
        if (sameNumberRow != null) {
            issues.add(new Issue(row, no, IssueType.CONFLICTING_INVOICE_NUMBER, EXCLUDED,
                    "Row " + sameNumberRow + " has the same invoice number with different details.",
                    "Kept the first row and excluded this one. Ask the wholesaler which is correct."));
            return null;
        }

        // 4. Period
        if (!period.contains(r.invoiceDate())) {
            issues.add(new Issue(row, no, IssueType.DATE_OUTSIDE_PERIOD, EXCLUDED,
                    "Invoice date " + r.invoiceDate() + " is outside " + period.start() + " to " + period.end()
                            + ". Possibly a mistyped year.",
                    "Row excluded from the 12-month analysis."));
            return null;
        }

        // 5. Document type and status
        DocumentType type = isCreditNote(r) ? DocumentType.CREDIT_NOTE : DocumentType.INVOICE;
        PaymentStatus status = type == DocumentType.CREDIT_NOTE ? PaymentStatus.NOT_APPLICABLE : parseStatus(r.status());
        if (status == null) {
            status = r.paidDate() != null ? PaymentStatus.PAID : PaymentStatus.UNPAID;
            issues.add(new Issue(row, no, IssueType.UNKNOWN_STATUS, WARNING,
                    "Status '" + r.status() + "' is not one of Paid, Unpaid, Partially Paid, Credit Note.",
                    "Treated as " + status + " based on whether a paid date is present."));
        }

        // Credit notes reduce revenue, so make sure they're negative whatever the export did.
        BigDecimal subtotal = type == DocumentType.CREDIT_NOTE ? r.subtotal().abs().negate() : r.subtotal();

        // 6. VAT and total
        BigDecimal expectedVat = scale(subtotal.multiply(VAT_RATE));
        BigDecimal vat = r.vat();
        if (vat == null) {
            vat = r.total() != null ? r.total().subtract(subtotal) : expectedVat;
        }
        if (type == DocumentType.CREDIT_NOTE) vat = vat.abs().negate();
        if (vat.subtract(expectedVat).abs().compareTo(TOLERANCE) > 0) {
            String pct = subtotal.signum() == 0 ? "?" :
                    vat.multiply(BigDecimal.valueOf(100)).divide(subtotal, 1, RoundingMode.HALF_UP) + "%";
            issues.add(new Issue(row, no, IssueType.VAT_MISMATCH, WARNING,
                    "VAT is SAR " + vat + " (" + pct + "), but 15% of the subtotal is SAR " + expectedVat + ".",
                    "Kept. Revenue is measured before VAT, so the analysis isn't affected. Check the tax code with the wholesaler."));
        }
        BigDecimal expectedTotal = subtotal.add(vat);
        BigDecimal total = r.total();
        if (type == DocumentType.CREDIT_NOTE && total != null) total = total.abs().negate();
        if (total == null) {
            total = expectedTotal;
        } else if (total.subtract(expectedTotal).abs().compareTo(TOLERANCE) > 0) {
            issues.add(new Issue(row, no, IssueType.TOTAL_MISMATCH, WARNING,
                    "Total is SAR " + total + " but subtotal + VAT is SAR " + expectedTotal + ".",
                    "Total recalculated as subtotal + VAT."));
            total = expectedTotal;
        }

        // 7. Due date
        Integer terms = r.paymentTermsDays();
        LocalDate due = r.dueDate();
        if (type == DocumentType.CREDIT_NOTE) {
            due = due != null ? due : r.invoiceDate();
        } else if (due == null) {
            int days = terms != null ? terms : DEFAULT_TERMS_DAYS;
            due = r.invoiceDate().plusDays(days);
            issues.add(new Issue(row, no, IssueType.MISSING_DUE_DATE, WARNING,
                    "No due date.",
                    "Assumed invoice date + " + days + " days" + (terms == null ? " (default terms)." : " (payment terms).")));
        }

        // 8. Payment
        LocalDate paidDate = r.paidDate();
        BigDecimal paid = r.amountPaid();
        boolean reliable = true;
        switch (status) {
            case PAID -> {
                paid = total;
                if (paidDate == null) {
                    reliable = false;
                    issues.add(new Issue(row, no, IssueType.PAID_WITHOUT_DATE, WARNING,
                            "Marked Paid but has no paid date.",
                            "Counted as paid. Left out of payment-speed metrics."));
                }
            }
            case PARTIALLY_PAID -> paid = paid == null ? BigDecimal.ZERO : paid.min(total);
            case UNPAID -> {
                paid = BigDecimal.ZERO;
                paidDate = null;
            }
            case NOT_APPLICABLE -> {
                paid = BigDecimal.ZERO;
                paidDate = null;
                reliable = false;
            }
        }
        if (paidDate != null && paidDate.isBefore(r.invoiceDate())) {
            issues.add(new Issue(row, no, IssueType.PAID_BEFORE_INVOICE, WARNING,
                    "Paid date " + paidDate + " is before the invoice date " + r.invoiceDate() + ".",
                    "Counted as paid. Left out of payment-speed metrics."));
            paidDate = null;
            reliable = false;
        }

        // 9. Customer VAT number (needed to verify the buyer exists)
        String custVat = r.customerVat() == null ? null : r.customerVat().replaceAll("\\s", "");
        if (type == DocumentType.INVOICE) {
            if (custVat == null) {
                issues.add(new Issue(row, no, IssueType.MISSING_CUSTOMER_VAT, WARNING,
                        "Customer '" + r.customerName() + "' has no VAT number on this invoice.",
                        "Kept. The buyer can't be verified against ZATCA records for this invoice."));
            } else if (!SAUDI_VAT.matcher(custVat).matches()) {
                issues.add(new Issue(row, no, IssueType.INVALID_CUSTOMER_VAT, WARNING,
                        "Customer VAT number '" + custVat + "' isn't 15 digits starting and ending with 3.",
                        "Kept. Flag for verification."));
            }
        }

        String name = r.customerName() != null ? r.customerName().trim() : r.customerId();
        String key = r.customerId() != null ? "ID:" + r.customerId().trim() : "NAME:" + normaliseName(name);

        return new CleanInvoice(row, no, type, r.invoiceDate(), due, key, name, custVat,
                r.segment(), r.city(), r.category(), terms, scale(subtotal), scale(vat), scale(total),
                status, paidDate, scale(paid), reliable);
    }

    private static boolean isCreditNote(RawInvoiceRow r) {
        String s = r.status() == null ? "" : r.status().toLowerCase();
        return s.contains("credit") || r.subtotal().signum() < 0;
    }

    private static PaymentStatus parseStatus(String s) {
        if (s == null) return null;
        String n = s.toLowerCase().replaceAll("[^a-z]", "");
        return switch (n) {
            case "paid", "settled", "closed" -> PaymentStatus.PAID;
            case "unpaid", "open", "outstanding", "due", "overdue" -> PaymentStatus.UNPAID;
            case "partiallypaid", "partial", "partpaid" -> PaymentStatus.PARTIALLY_PAID;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ customer names

    /**
     * The same customer is often typed differently ("Al-Rayyan Hypermarkets" vs "AL-RAYYAN HYPERMARKETS ").
     * Group rows by customer key and use the most common spelling everywhere.
     */
    private List<CleanInvoice> unifyCustomerNames(List<CleanInvoice> rows, List<Issue> issues) {
        Map<String, String> canonical = rows.stream()
                .collect(Collectors.groupingBy(CleanInvoice::customerKey,
                        Collectors.groupingBy(CleanInvoice::customerName, Collectors.counting())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().entrySet().stream()
                        .max(Map.Entry.<String, Long>comparingByValue()
                                .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                        .map(Map.Entry::getKey).orElseThrow()));

        Function<CleanInvoice, CleanInvoice> fix = inv -> {
            String best = canonical.get(inv.customerKey());
            if (best.equals(inv.customerName())) return inv;
            issues.add(new Issue(inv.sourceRow(), inv.invoiceNumber(), IssueType.CUSTOMER_NAME_VARIANT, INFO,
                    "Customer written as '" + inv.customerName() + "'.",
                    "Grouped under '" + best + "'."));
            return new CleanInvoice(inv.sourceRow(), inv.invoiceNumber(), inv.type(), inv.invoiceDate(),
                    inv.dueDate(), inv.customerKey(), best, inv.customerVat(), inv.segment(), inv.city(),
                    inv.category(), inv.paymentTermsDays(), inv.subtotal(), inv.vat(), inv.total(),
                    inv.status(), inv.paidDate(), inv.amountPaid(), inv.paidDateReliable());
        };
        return rows.stream().map(fix).toList();
    }

    private static String normaliseName(String name) {
        return name.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}

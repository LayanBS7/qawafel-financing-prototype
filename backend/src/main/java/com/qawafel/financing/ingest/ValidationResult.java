package com.qawafel.financing.ingest;

import com.qawafel.financing.domain.DocumentType;
import com.qawafel.financing.domain.IssueSeverity;
import com.qawafel.financing.domain.IssueType;
import com.qawafel.financing.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Output of {@link InvoiceValidator}: clean rows, everything that was wrong, and the period analysed. */
public record ValidationResult(
        String companyName,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate asOfDate,
        int rowsRead,
        List<CleanInvoice> invoices,
        List<Issue> issues
) {

    public int rowsExcluded() {
        return rowsRead - invoices.size();
    }

    public record CleanInvoice(
            int sourceRow,
            String invoiceNumber,
            DocumentType type,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String customerKey,
            String customerName,
            String customerVat,
            String segment,
            String city,
            String category,
            Integer paymentTermsDays,
            BigDecimal subtotal,
            BigDecimal vat,
            BigDecimal total,
            PaymentStatus status,
            LocalDate paidDate,
            BigDecimal amountPaid,
            boolean paidDateReliable
    ) {
    }

    public record Issue(
            int sourceRow,
            String invoiceNumber,
            IssueType type,
            IssueSeverity severity,
            String message,
            String actionTaken
    ) {
    }
}

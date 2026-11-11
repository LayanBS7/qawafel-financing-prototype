package com.qawafel.financing.web;

import com.qawafel.financing.domain.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Request and response shapes for the REST API, kept separate from the JPA entities. */
public final class Dtos {

    private Dtos() {
    }

    public record ApplicationSummary(
            Long id,
            String companyName,
            String sourceFileName,
            Instant uploadedAt,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal requestedAmount,
            int rowsRead,
            int rowsAccepted,
            int rowsExcluded,
            ApplicationStatus status,
            AnalystDecision decision
    ) {
        public static ApplicationSummary of(FinancingApplication a) {
            return new ApplicationSummary(a.getId(), a.getCompanyName(), a.getSourceFileName(), a.getUploadedAt(),
                    a.getPeriodStart(), a.getPeriodEnd(), a.getRequestedAmount(), a.getRowsRead(),
                    a.getRowsAccepted(), a.getRowsExcluded(), a.getStatus(), a.getDecision());
        }
    }

    public record IssueView(
            int sourceRow,
            String invoiceNumber,
            String type,
            String label,
            IssueSeverity severity,
            String message,
            String actionTaken
    ) {
        public static IssueView of(DataIssue i) {
            return new IssueView(i.getSourceRow(), i.getInvoiceNumber(), i.getType().name(), i.getType().label(),
                    i.getSeverity(), i.getMessage(), i.getActionTaken());
        }
    }

    public record InvoiceView(
            int sourceRow,
            String invoiceNumber,
            DocumentType type,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String customerKey,
            String customerName,
            BigDecimal subtotal,
            BigDecimal total,
            PaymentStatus paymentStatus,
            LocalDate paidDate,
            BigDecimal outstanding
    ) {
        public static InvoiceView of(Invoice i) {
            return new InvoiceView(i.getSourceRow(), i.getInvoiceNumber(), i.getType(), i.getInvoiceDate(),
                    i.getDueDate(), i.getCustomerKey(), i.getCustomerName(), i.getSubtotal(), i.getTotal(),
                    i.getPaymentStatus(), i.getPaidDate(), i.outstanding());
        }
    }

    public record DecisionRequest(
            @NotNull AnalystDecision decision,
            @Size(max = 2000) String note
    ) {
    }

    public record ErrorResponse(int status, String error, String message) {
    }
}

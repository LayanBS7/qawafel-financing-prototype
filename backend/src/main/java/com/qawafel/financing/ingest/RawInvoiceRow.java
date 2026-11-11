package com.qawafel.financing.ingest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * One spreadsheet row as read from the file, before any business validation.
 * Fields are null when the cell is empty. {@code unreadable} holds cells that had a value
 * we could not convert (e.g. "31/31/2026" in a date column), keyed by column.
 */
public record RawInvoiceRow(
        int rowNumber,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String customerId,
        String customerName,
        String customerVat,
        String segment,
        String city,
        String category,
        Integer paymentTermsDays,
        BigDecimal subtotal,
        BigDecimal vat,
        BigDecimal total,
        String status,
        LocalDate paidDate,
        BigDecimal amountPaid,
        String notes,
        Map<Column, String> unreadable
) {

    /** Every field except the row number, used to spot exact duplicates. */
    public String contentSignature() {
        return String.join("|",
                String.valueOf(invoiceNumber), String.valueOf(invoiceDate), String.valueOf(dueDate),
                String.valueOf(customerId), String.valueOf(customerName), String.valueOf(customerVat),
                String.valueOf(subtotal), String.valueOf(vat), String.valueOf(total), String.valueOf(status),
                String.valueOf(paidDate), String.valueOf(amountPaid));
    }
}

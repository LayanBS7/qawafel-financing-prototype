package com.qawafel.financing.ingest;

import java.time.LocalDate;
import java.util.List;

/**
 * Result of reading a workbook. Company details come from an optional "Company Info" sheet;
 * any of them may be null when the sheet is missing.
 */
public record ParsedWorkbook(
        List<RawInvoiceRow> rows,
        String companyName,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate exportedOn
) {
}

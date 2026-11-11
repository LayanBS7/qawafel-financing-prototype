package com.qawafel.financing.ingest;

import com.qawafel.financing.domain.IssueType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real sample file through the parser and validator and checks that every
 * deliberately injected problem (see sample-data/injected_issues.md) is caught.
 */
class InvoiceValidatorTest {

    private static ValidationResult result;
    private static Map<IssueType, Long> counts;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = InvoiceValidatorTest.class.getResourceAsStream("/sample/invoices_12m.xlsx")) {
            ParsedWorkbook wb = new ExcelInvoiceParser().parse(in);
            result = new InvoiceValidator().validate(wb);
        }
        counts = result.issues().stream()
                .collect(Collectors.groupingBy(ValidationResult.Issue::type, Collectors.counting()));
        System.out.println("Issue counts: " + counts);
    }

    @Test
    void readsPeriodFromCompanyInfoSheet() {
        assertThat(result.periodStart()).isEqualTo(LocalDate.of(2025, 11, 1));
        assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(result.asOfDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(result.companyName()).startsWith("Wadi Al-Khair");
    }

    @Test
    void catchesEveryInjectedIssue() {
        assertThat(counts.get(IssueType.DUPLICATE_ROW)).isEqualTo(6);
        assertThat(counts.get(IssueType.MISSING_CUSTOMER_VAT)).isEqualTo(5);
        assertThat(counts.get(IssueType.VAT_MISMATCH)).isEqualTo(4);
        assertThat(counts.get(IssueType.TOTAL_MISMATCH)).isEqualTo(3);
        assertThat(counts.get(IssueType.PAID_BEFORE_INVOICE)).isEqualTo(3);
        assertThat(counts.get(IssueType.PAID_WITHOUT_DATE)).isEqualTo(3);
        assertThat(counts.get(IssueType.DATE_OUTSIDE_PERIOD)).isEqualTo(2);
        assertThat(counts.get(IssueType.CUSTOMER_NAME_VARIANT)).isEqualTo(4);
        assertThat(counts.get(IssueType.MISSING_REQUIRED_FIELD)).isEqualTo(2);
    }

    @Test
    void excludedRowsAreDuplicatesWrongPeriodAndMissingAmounts() {
        assertThat(result.rowsExcluded()).isEqualTo(6 + 2 + 2);
    }

    @Test
    void creditNotesAreNegative() {
        assertThat(result.invoices().stream()
                .filter(i -> i.type() == com.qawafel.financing.domain.DocumentType.CREDIT_NOTE)
                .allMatch(i -> i.subtotal().signum() < 0)).isTrue();
    }
}

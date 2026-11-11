package com.qawafel.financing.analysis;

import com.qawafel.financing.analysis.AnalysisResult.CustomerFlag;
import com.qawafel.financing.analysis.AnalysisResult.CustomerSummary;
import com.qawafel.financing.domain.DataIssue;
import com.qawafel.financing.domain.FinancingApplication;
import com.qawafel.financing.domain.Invoice;
import com.qawafel.financing.ingest.ExcelInvoiceParser;
import com.qawafel.financing.ingest.InvoiceValidator;
import com.qawafel.financing.ingest.ValidationResult;
import com.qawafel.financing.service.ApplicationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sample data has five deliberate "stories" (see data-generator/README.md).
 * A useful analysis must surface every one of them, and nothing else.
 */
class AnalysisServiceTest {

    private static AnalysisResult result;

    @BeforeAll
    static void analyseSample() throws Exception {
        ValidationResult v;
        try (InputStream in = AnalysisServiceTest.class.getResourceAsStream("/sample/invoices_12m.xlsx")) {
            v = new InvoiceValidator().validate(new ExcelInvoiceParser().parse(in));
        }
        FinancingApplication app = new FinancingApplication(v.companyName(), "sample.xlsx", v.periodStart(),
                v.periodEnd(), v.asOfDate(), new BigDecimal("3000000"));
        app.recordRowCounts(v.rowsRead(), v.invoices().size(), v.rowsExcluded());
        List<Invoice> invoices = v.invoices().stream().map(c -> ApplicationService.toEntity(app, c)).toList();
        List<DataIssue> issues = v.issues().stream().map(i -> new DataIssue(app, i.sourceRow(), i.invoiceNumber(),
                i.type(), i.severity(), i.message(), i.actionTaken())).toList();

        result = new AnalysisService(new RiskScorer(), new FinancingCalculator()).analyze(app, invoices, issues);
    }

    @Test
    void findsTheConcentrationRisk() {
        assertThat(result.concentration().topCustomers().get(0).name()).isEqualTo("Al-Rayyan Hypermarkets");
        assertThat(result.concentration().topCustomerShare()).isBetween(0.20, 0.30);
        assertThat(flagsOf("Al-Rayyan Hypermarkets")).containsExactly(CustomerFlag.LARGEST_CUSTOMER);
    }

    @Test
    void findsTheCustomerWhoStartedPayingSlower() {
        assertThat(flagsOf("Najd Family Hyper")).contains(CustomerFlag.PAYING_SLOWER);
    }

    @Test
    void findsTheChronicLatePayer() {
        assertThat(flagsOf("Nakheel Corner Store")).contains(CustomerFlag.CHRONIC_LATE_PAYER);
    }

    @Test
    void findsTheLostAndTheNewCustomer() {
        assertThat(flagsOf("Qimmat Al-Jawda Supermarket")).contains(CustomerFlag.STOPPED_BUYING);
        assertThat(flagsOf("Al-Bustan Fresh")).contains(CustomerFlag.NEW_CUSTOMER);
    }

    @Test
    void doesNotFlagOrdinaryCustomers() {
        List<String> flagged = result.customers().stream()
                .filter(c -> !c.flags().isEmpty()).map(CustomerSummary::name).toList();
        assertThat(flagged).containsExactlyInAnyOrder("Al-Rayyan Hypermarkets", "Najd Family Hyper",
                "Nakheel Corner Store", "Qimmat Al-Jawda Supermarket", "Al-Bustan Fresh");
    }

    @Test
    void showsSeasonality() {
        // February (Ramadan stocking) should be the strongest month, July (summer) among the weakest.
        var monthly = result.monthly();
        var feb = monthly.stream().filter(m -> m.month().equals("2026-02")).findFirst().orElseThrow();
        var jul = monthly.stream().filter(m -> m.month().equals("2026-07")).findFirst().orElseThrow();
        assertThat(monthly.stream().mapToDouble(AnalysisResult.MonthlyPoint::netSales).max().orElseThrow())
                .isEqualTo(feb.netSales());
        assertThat(jul.netSales()).isLessThan(result.headline().averageMonthlyRevenue());
    }

    @Test
    void scoreIsModerateAndEveryPointIsExplained() {
        assertThat(result.risk().band()).isEqualTo("Moderate");
        assertThat(result.risk().maxScore()).isEqualTo(100);
        assertThat(result.risk().factors()).allSatisfy(f -> {
            assertThat(f.explanation()).isNotBlank();
            assertThat(f.points()).isBetween(0, f.maxPoints());
        });
    }

    @Test
    void financingLimitIsBelowEligibleReceivables() {
        var f = result.financing();
        assertThat(f.suggestedLimit()).isPositive();
        assertThat(f.suggestedLimit()).isLessThanOrEqualTo(f.eligibleReceivables() * 0.8);
        assertThat(f.eligibleReceivables()).isLessThan(result.headline().openReceivables());
        assertThat(f.conditions()).anyMatch(c -> c.contains("Nakheel Corner Store"));
    }

    @Test
    void requestAboveTheLimitIsCalledOutButDoesNotChangeTheScore() {
        var f = result.financing();
        var bigRequest = new AnalysisResult.FinancingEstimate(f.method(), f.steps(), f.eligibleReceivables(),
                f.advanceRate(), f.suggestedLimit(), 10_000_000.0, 37.4, f.conditions());
        var risk = AnalysisService.withRequestCheck(result.risk(), bigRequest);
        assertThat(risk.score()).isEqualTo(result.risk().score());
        assertThat(risk.recommendation()).contains("above the suggested limit").contains("SAR 10,000,000");

        // The sample asks for SAR 3M, which fits, so nothing is added.
        assertThat(result.risk().recommendation()).doesNotContain("above the suggested limit");
        assertThat(f.conditions()).noneMatch(c -> c.startsWith("Reduce the amount"));
    }

    private static List<CustomerFlag> flagsOf(String name) {
        return result.customers().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().flags();
    }
}

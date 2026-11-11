package com.qawafel.financing.analysis;

import com.qawafel.financing.domain.AnalystDecision;
import com.qawafel.financing.domain.ApplicationStatus;
import com.qawafel.financing.domain.IssueSeverity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the analyst sees for one financing application. Amounts are in SAR.
 * Revenue figures exclude VAT; receivable figures include VAT (that's what customers owe).
 */
public record AnalysisResult(
        Application application,
        Headline headline,
        List<MonthlyPoint> monthly,
        Concentration concentration,
        CollectionMetrics collections,
        List<CustomerSummary> customers,
        RiskAssessment risk,
        FinancingEstimate financing,
        DataQuality dataQuality
) {

    public record Application(
            Long id,
            String companyName,
            String sourceFileName,
            Instant uploadedAt,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate asOfDate,
            Double requestedAmount,
            ApplicationStatus status,
            AnalystDecision decision,
            String decisionNote,
            Instant decidedAt
    ) {
    }

    public record Headline(
            double netRevenue,
            double averageMonthlyRevenue,
            /** Last 3 months vs first 3 months of the period, in %. */
            double revenueTrendPct,
            int activeCustomers,
            int invoiceCount,
            double openReceivables,
            double overdueReceivables,
            double daysSalesOutstanding,
            double averageDaysLate
    ) {
    }

    public record MonthlyPoint(
            String month,
            double grossSales,
            double creditNotes,
            double netSales,
            int invoiceCount,
            int activeCustomers
    ) {
    }

    public record Concentration(
            double topCustomerShare,
            double top5Share,
            /** Herfindahl-Hirschman index, 0-10,000. Above 1,500 = moderately concentrated. */
            double hhi,
            List<CustomerShare> topCustomers
    ) {
    }

    public record CustomerShare(String customerKey, String name, double revenue, double share) {
    }

    public record CollectionMetrics(
            double daysSalesOutstanding,
            double averageDaysToPay,
            double averageDaysLate,
            /** Share of paid invoices paid on or before the due date, %. */
            double onTimeRate,
            int paidInvoicesMeasured,
            List<AgingBucket> aging
    ) {
    }

    public record AgingBucket(String label, double amount, int invoiceCount, double share) {
    }

    public record CustomerSummary(
            String customerKey,
            String name,
            String segment,
            String city,
            double revenue,
            double share,
            int invoiceCount,
            LocalDate firstInvoice,
            LocalDate lastInvoice,
            Double averageDaysLate,
            /** Average days late at the start of the period, the customer's normal behaviour. */
            Double baselineDaysLate,
            Double recentDaysLate,
            double openBalance,
            double overdue60Balance,
            List<CustomerFlag> flags
    ) {
    }

    public enum CustomerFlag {
        LARGEST_CUSTOMER,
        PAYING_SLOWER,
        CHRONIC_LATE_PAYER,
        STOPPED_BUYING,
        NEW_CUSTOMER,
        HIGH_OVERDUE
    }

    public record RiskAssessment(
            int score,
            int maxScore,
            String band,
            String recommendation,
            List<ScoreFactor> factors,
            List<String> strengths,
            List<String> concerns
    ) {
    }

    public record ScoreFactor(
            String name,
            int points,
            int maxPoints,
            /** The measured value, formatted for display, e.g. "24.3%". */
            String value,
            String explanation,
            Rating rating
    ) {
    }

    public enum Rating { GOOD, FAIR, POOR }

    public record FinancingEstimate(
            String method,
            List<FinancingStep> steps,
            double eligibleReceivables,
            double advanceRate,
            double suggestedLimit,
            Double requestedAmount,
            /** suggestedLimit / requestedAmount, %; null when no amount was requested. */
            Double requestCoverage,
            List<String> conditions
    ) {
    }

    public record FinancingStep(String label, double amount, String explanation) {
    }

    public record DataQuality(
            int rowsRead,
            int rowsAccepted,
            int rowsExcluded,
            int warnings,
            int info,
            List<IssueCount> byType
    ) {
    }

    public record IssueCount(String type, String label, IssueSeverity severity, long count) {
    }
}

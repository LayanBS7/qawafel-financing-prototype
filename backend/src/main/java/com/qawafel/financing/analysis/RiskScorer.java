package com.qawafel.financing.analysis;

import com.qawafel.financing.analysis.AnalysisResult.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A transparent, rule-based score out of 100. Every factor shows the value it measured, the points
 * it earned and why, so a bank can challenge any part of it. The thresholds are prototype
 * assumptions and would be calibrated with the bank's own credit policy.
 *
 * <pre>
 * Factor                     Max   What it measures
 * Revenue trend               15   last 3 months vs first 3 months
 * Revenue stability           10   month-to-month variation
 * Customer concentration      20   share of the largest customer (and top 5)
 * Collection speed            20   days sales outstanding (DSO)
 * Overdue receivables         15   share of receivables more than 60 days past due
 * Customer base stability     10   lost customers and customers paying slower
 * Data quality                10   rows excluded or flagged in the upload
 * </pre>
 */
@Component
public class RiskScorer {

    public record Inputs(
            double revenueTrendPct,
            double revenueVolatility,
            Concentration concentration,
            CollectionMetrics collections,
            List<CustomerSummary> customers,
            DataQuality dataQuality
    ) {
    }

    public RiskAssessment score(Inputs in) {
        List<ScoreFactor> factors = List.of(
                revenueTrend(in.revenueTrendPct()),
                revenueStability(in.revenueVolatility()),
                concentration(in.concentration()),
                collectionSpeed(in.collections()),
                overdue(in.collections()),
                customerStability(in.customers()),
                dataQuality(in.dataQuality()));

        int score = factors.stream().mapToInt(ScoreFactor::points).sum();
        int max = factors.stream().mapToInt(ScoreFactor::maxPoints).sum();

        String band;
        String recommendation;
        if (score >= 75) {
            band = "Strong";
            recommendation = "Recommend sending to the bank.";
        } else if (score >= 55) {
            band = "Moderate";
            recommendation = "Recommend with conditions: see the financing conditions below.";
        } else {
            band = "Weak";
            recommendation = "Do not send yet: resolve the concerns below or request more information.";
        }

        List<String> strengths = new ArrayList<>();
        List<String> concerns = new ArrayList<>();
        for (ScoreFactor f : factors) {
            if (f.rating() == Rating.GOOD) strengths.add(f.explanation());
            else concerns.add(f.explanation());
        }
        return new RiskAssessment(score, max, band, recommendation, factors, strengths, concerns);
    }

    // ------------------------------------------------------------------ factors

    private ScoreFactor revenueTrend(double pct) {
        int pts;
        String why;
        if (pct >= 5) {
            pts = 15;
            why = "Sales are growing: the last 3 months are " + pct(pct) + " above the first 3.";
        } else if (pct >= -5) {
            pts = 11;
            why = "Sales are broadly flat (" + pct(pct) + " last 3 months vs first 3).";
        } else if (pct >= -15) {
            pts = 6;
            why = "Sales are declining: the last 3 months are " + pct(pct) + " vs the first 3.";
        } else {
            pts = 0;
            why = "Sales dropped sharply: " + pct(pct) + " last 3 months vs first 3.";
        }
        return factor("Revenue trend", pts, 15, pct(pct), why);
    }

    private ScoreFactor revenueStability(double cv) {
        int pts = cv <= 0.15 ? 10 : cv <= 0.25 ? 7 : cv <= 0.35 ? 4 : 0;
        String level = cv <= 0.15 ? "steady" : cv <= 0.25 ? "moderately seasonal" : "volatile";
        String why = "Monthly sales are " + level + " (they typically move " + Math.round(cv * 100)
                + "% around the average).";
        return factor("Revenue stability", pts, 10, Math.round(cv * 100) + "%", why);
    }

    private ScoreFactor concentration(Concentration c) {
        double top = c.topCustomerShare() * 100;
        int pts = top <= 15 ? 20 : top <= 25 ? 13 : top <= 35 ? 6 : 0;
        String name = c.topCustomers().isEmpty() ? "-" : c.topCustomers().get(0).name();
        String why = name + " is " + pct1(top) + " of sales";
        if (c.top5Share() > 0.60) {
            pts = Math.max(0, pts - 5);
            why += ", and the top 5 customers are " + pct1(c.top5Share() * 100);
        }
        why += top <= 15 ? ": sales are well spread." : ": losing this customer would hurt repayment.";
        return factor("Customer concentration", pts, 20, pct1(top), why);
    }

    private ScoreFactor collectionSpeed(CollectionMetrics c) {
        double dso = c.daysSalesOutstanding();
        int pts = dso <= 45 ? 20 : dso <= 60 ? 15 : dso <= 75 ? 10 : dso <= 90 ? 5 : 0;
        String why = "It takes about " + Math.round(dso) + " days of sales to turn into cash (DSO); customers pay on average "
                + Math.round(c.averageDaysLate()) + " days after the due date.";
        return factor("Collection speed", pts, 20, Math.round(dso) + " days", why);
    }

    private ScoreFactor overdue(CollectionMetrics c) {
        double over60 = c.aging().stream()
                .filter(b -> b.label().startsWith("61") || b.label().startsWith("90"))
                .mapToDouble(AgingBucket::share).sum() * 100;
        int pts = over60 <= 5 ? 15 : over60 <= 10 ? 10 : over60 <= 20 ? 5 : 0;
        String why = pct1(over60) + " of what customers owe is more than 60 days past due"
                + (over60 <= 5 ? ": healthy." : ": these may never be collected.");
        return factor("Overdue receivables", pts, 15, pct1(over60), why);
    }

    private ScoreFactor customerStability(List<CustomerSummary> customers) {
        List<String> lost = customers.stream()
                .filter(c -> c.flags().contains(CustomerFlag.STOPPED_BUYING) && c.share() >= 0.02)
                .map(CustomerSummary::name).toList();
        List<String> slower = customers.stream()
                .filter(c -> c.flags().contains(CustomerFlag.PAYING_SLOWER))
                .map(CustomerSummary::name).toList();
        int pts = Math.max(0, 10 - 4 * lost.size() - 3 * slower.size());
        List<String> parts = new ArrayList<>();
        if (!lost.isEmpty()) parts.add("stopped buying: " + String.join(", ", lost));
        if (!slower.isEmpty()) parts.add("paying slower recently: " + String.join(", ", slower));
        String why = parts.isEmpty() ? "No significant customers lost and none paying noticeably slower."
                : "Watch " + String.join("; ", parts) + ".";
        return factor("Customer base stability", pts, 10, lost.size() + " lost, " + slower.size() + " slower", why);
    }

    private ScoreFactor dataQuality(DataQuality q) {
        double excluded = q.rowsRead() == 0 ? 0 : 100.0 * q.rowsExcluded() / q.rowsRead();
        double warned = q.rowsRead() == 0 ? 0 : 100.0 * q.warnings() / q.rowsRead();
        int pts = excluded <= 1 && warned <= 3 ? 10 : excluded <= 3 ? 6 : 2;
        String why = q.rowsExcluded() + " of " + q.rowsRead() + " rows excluded and " + q.warnings()
                + " flagged" + (pts == 10 ? ": the file is reliable enough to decide on." : ": numbers may be off.");
        return factor("Data quality", pts, 10, pct1(excluded) + " excluded", why);
    }

    // ------------------------------------------------------------------ helpers

    private static ScoreFactor factor(String name, int pts, int max, String value, String why) {
        double ratio = (double) pts / max;
        Rating r = ratio >= 0.75 ? Rating.GOOD : ratio >= 0.45 ? Rating.FAIR : Rating.POOR;
        return new ScoreFactor(name, pts, max, value, why, r);
    }

    private static String pct(double v) {
        return (v >= 0 ? "+" : "") + Math.round(v) + "%";
    }

    private static String pct1(double v) {
        return String.format("%.1f%%", v);
    }
}

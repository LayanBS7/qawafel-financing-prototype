package com.qawafel.financing.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One financing request: a wholesaler's uploaded invoice file plus the analyst's decision.
 * Analysis results are not stored; they are recalculated from the invoices on every read,
 * so changing a rule never leaves stale numbers behind.
 */
@Entity
@Table(name = "financing_application")
public class FinancingApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String companyName;

    private String sourceFileName;

    @Column(nullable = false)
    private Instant uploadedAt;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    /** Date the data reflects: invoices unpaid on this date count as open receivables. */
    @Column(nullable = false)
    private LocalDate asOfDate;

    /** Amount the wholesaler asked for, if known. */
    @Column(precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    private int rowsRead;
    private int rowsAccepted;
    private int rowsExcluded;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.ANALYZED;

    @Enumerated(EnumType.STRING)
    private AnalystDecision decision;

    @Column(length = 2000)
    private String decisionNote;

    private Instant decidedAt;

    protected FinancingApplication() {
    }

    public FinancingApplication(String companyName, String sourceFileName, LocalDate periodStart,
                                LocalDate periodEnd, LocalDate asOfDate, BigDecimal requestedAmount) {
        this.companyName = companyName;
        this.sourceFileName = sourceFileName;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.asOfDate = asOfDate;
        this.requestedAmount = requestedAmount;
        this.uploadedAt = Instant.now();
    }

    public void recordRowCounts(int read, int accepted, int excluded) {
        this.rowsRead = read;
        this.rowsAccepted = accepted;
        this.rowsExcluded = excluded;
    }

    public void decide(AnalystDecision decision, String note) {
        this.decision = decision;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
        this.status = ApplicationStatus.DECIDED;
    }

    public Long getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getSourceFileName() { return sourceFileName; }
    public Instant getUploadedAt() { return uploadedAt; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public int getRowsRead() { return rowsRead; }
    public int getRowsAccepted() { return rowsAccepted; }
    public int getRowsExcluded() { return rowsExcluded; }
    public ApplicationStatus getStatus() { return status; }
    public AnalystDecision getDecision() { return decision; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getDecidedAt() { return decidedAt; }
}

package com.qawafel.financing.domain;

import jakarta.persistence.*;

/** A problem found while reading the uploaded file, and what the system did about it. */
@Entity
@Table(name = "data_issue", indexes = @Index(columnList = "application_id"))
public class DataIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private FinancingApplication application;

    /** Excel row number (1 = header row). 0 for file-level issues. */
    private int sourceRow;

    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueSeverity severity;

    @Column(nullable = false, length = 1000)
    private String message;

    /** What the system did: excluded the row, corrected a value, or only flagged it. */
    @Column(nullable = false, length = 500)
    private String actionTaken;

    protected DataIssue() {
    }

    public DataIssue(FinancingApplication application, int sourceRow, String invoiceNumber, IssueType type,
                     IssueSeverity severity, String message, String actionTaken) {
        this.application = application;
        this.sourceRow = sourceRow;
        this.invoiceNumber = invoiceNumber;
        this.type = type;
        this.severity = severity;
        this.message = message;
        this.actionTaken = actionTaken;
    }

    public Long getId() { return id; }
    public int getSourceRow() { return sourceRow; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public IssueType getType() { return type; }
    public IssueSeverity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public String getActionTaken() { return actionTaken; }
}

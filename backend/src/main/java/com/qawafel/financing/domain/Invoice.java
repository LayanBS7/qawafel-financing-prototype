package com.qawafel.financing.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One accepted row from the uploaded file (an invoice or a credit note), after cleaning. */
@Entity
@Table(name = "invoice", indexes = @Index(columnList = "application_id"))
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private FinancingApplication application;

    /** Row number in the Excel sheet, so the analyst can find the original. */
    private int sourceRow;

    @Column(nullable = false)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    /** Stable key for the buyer: customer ID if the file has one, otherwise the normalised name. */
    @Column(nullable = false)
    private String customerKey;

    @Column(nullable = false)
    private String customerName;

    private String customerVatNumber;
    private String customerSegment;
    private String city;
    private String productCategory;
    private Integer paymentTermsDays;

    /** Amount before VAT. Negative for credit notes. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal vat;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    private LocalDate paidDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid;

    /**
     * False when the paid date is missing or impossible. The invoice still counts for revenue,
     * but is left out of payment-speed metrics.
     */
    private boolean paidDateReliable;

    protected Invoice() {
    }

    public Invoice(FinancingApplication application, int sourceRow, String invoiceNumber, DocumentType type,
                   LocalDate invoiceDate, LocalDate dueDate, String customerKey, String customerName,
                   String customerVatNumber, String customerSegment, String city, String productCategory,
                   Integer paymentTermsDays, BigDecimal subtotal, BigDecimal vat, BigDecimal total,
                   PaymentStatus paymentStatus, LocalDate paidDate, BigDecimal amountPaid,
                   boolean paidDateReliable) {
        this.application = application;
        this.sourceRow = sourceRow;
        this.invoiceNumber = invoiceNumber;
        this.type = type;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.customerKey = customerKey;
        this.customerName = customerName;
        this.customerVatNumber = customerVatNumber;
        this.customerSegment = customerSegment;
        this.city = city;
        this.productCategory = productCategory;
        this.paymentTermsDays = paymentTermsDays;
        this.subtotal = subtotal;
        this.vat = vat;
        this.total = total;
        this.paymentStatus = paymentStatus;
        this.paidDate = paidDate;
        this.amountPaid = amountPaid;
        this.paidDateReliable = paidDateReliable;
    }

    /** Amount still owed by the customer (incl. VAT). Zero for paid invoices and credit notes. */
    public BigDecimal outstanding() {
        if (type == DocumentType.CREDIT_NOTE || paymentStatus == PaymentStatus.PAID) {
            return BigDecimal.ZERO;
        }
        return total.subtract(amountPaid).max(BigDecimal.ZERO);
    }

    public Long getId() { return id; }
    public FinancingApplication getApplication() { return application; }
    public int getSourceRow() { return sourceRow; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public DocumentType getType() { return type; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public LocalDate getDueDate() { return dueDate; }
    public String getCustomerKey() { return customerKey; }
    public String getCustomerName() { return customerName; }
    public String getCustomerVatNumber() { return customerVatNumber; }
    public String getCustomerSegment() { return customerSegment; }
    public String getCity() { return city; }
    public String getProductCategory() { return productCategory; }
    public Integer getPaymentTermsDays() { return paymentTermsDays; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getVat() { return vat; }
    public BigDecimal getTotal() { return total; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public LocalDate getPaidDate() { return paidDate; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public boolean isPaidDateReliable() { return paidDateReliable; }
}

package com.qawafel.financing.domain;

public enum IssueType {
    MISSING_REQUIRED_FIELD("Missing required field"),
    INVALID_VALUE("Value could not be read"),
    DUPLICATE_ROW("Duplicate row"),
    CONFLICTING_INVOICE_NUMBER("Same invoice number, different details"),
    DATE_OUTSIDE_PERIOD("Date outside the 12-month period"),
    VAT_MISMATCH("VAT is not 15%"),
    TOTAL_MISMATCH("Total does not equal subtotal + VAT"),
    PAID_BEFORE_INVOICE("Paid before it was invoiced"),
    PAID_WITHOUT_DATE("Marked paid, no paid date"),
    MISSING_CUSTOMER_VAT("Missing customer VAT number"),
    INVALID_CUSTOMER_VAT("Customer VAT number has the wrong format"),
    CUSTOMER_NAME_VARIANT("Same customer, different name spelling"),
    UNKNOWN_STATUS("Unknown payment status"),
    MISSING_DUE_DATE("Missing due date");

    private final String label;

    IssueType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

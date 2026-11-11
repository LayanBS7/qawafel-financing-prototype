package com.qawafel.financing.ingest;

import java.util.List;

/**
 * Columns we understand, with the header spellings we accept for each.
 * Headers are matched after lower-casing and removing everything except letters and digits,
 * so "Invoice Date", "invoice_date" and "INVOICE-DATE" all match.
 */
public enum Column {
    INVOICE_NUMBER(true, "invoicenumber", "invoiceno", "invoice", "documentnumber", "number"),
    INVOICE_DATE(true, "invoicedate", "date", "issuedate"),
    DUE_DATE(false, "duedate", "paymentduedate"),
    CUSTOMER_ID(false, "customerid", "customercode", "clientid"),
    CUSTOMER_NAME(true, "customername", "customer", "client", "clientname", "buyer"),
    CUSTOMER_VAT(false, "customervatnumber", "customervat", "vatnumber", "buyervat"),
    SEGMENT(false, "customersegment", "segment", "channel"),
    CITY(false, "city"),
    CATEGORY(false, "productcategory", "category"),
    PAYMENT_TERMS(false, "paymenttermsdays", "paymentterms", "terms"),
    SUBTOTAL(true, "subtotalsar", "subtotal", "netamount", "amountexclvat", "amount"),
    VAT(false, "vatsar", "vat", "vatamount", "tax"),
    TOTAL(false, "totalsar", "total", "grossamount", "amountinclvat"),
    STATUS(true, "status", "paymentstatus"),
    PAID_DATE(false, "paiddate", "paymentdate", "datepaid"),
    AMOUNT_PAID(false, "amountpaidsar", "amountpaid", "paidamount"),
    NOTES(false, "notes", "note", "comments");

    private final boolean required;
    private final List<String> aliases;

    Column(boolean required, String... aliases) {
        this.required = required;
        this.aliases = List.of(aliases);
    }

    public boolean required() {
        return required;
    }

    public List<String> aliases() {
        return aliases;
    }

    public static String normalise(String header) {
        return header == null ? "" : header.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}

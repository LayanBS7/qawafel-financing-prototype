package com.qawafel.financing.domain;

public enum PaymentStatus {
    PAID,
    UNPAID,
    PARTIALLY_PAID,
    /** Credit notes reduce revenue; they are not collected. */
    NOT_APPLICABLE
}

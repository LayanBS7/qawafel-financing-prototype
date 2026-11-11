package com.qawafel.financing.domain;

public enum AnalystDecision {
    /** Send the package to the bank as is. */
    RECOMMEND,
    /** Send it, but with conditions (lower amount, excluded customers, ...). */
    RECOMMEND_WITH_CONDITIONS,
    /** Ask the wholesaler for more information first. */
    NEED_MORE_INFO,
    /** Do not send to the bank. */
    DECLINE
}

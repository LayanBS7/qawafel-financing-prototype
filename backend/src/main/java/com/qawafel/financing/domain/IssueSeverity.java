package com.qawafel.financing.domain;

public enum IssueSeverity {
    /** The row could not be trusted and was left out of the analysis. */
    EXCLUDED,
    /** The row was kept, but a value was corrected or is ignored for some metrics. */
    WARNING,
    /** Worth knowing, no effect on the numbers. */
    INFO
}

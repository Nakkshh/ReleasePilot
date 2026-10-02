package com.releasepilot.model;

public enum DraftStatus {
    PENDING,    // waiting for a human decision
    REJECTED,   // human said no
    EXPIRED,    // nobody decided in time
    APPROVED,   // human said yes; GitHub release not (yet) confirmed. May hold last_error
    RELEASED    // GitHub release exists
}
package com.releasepilot.model;

public enum ApprovalGate {
    OPEN,                    // can be approved as is
    NEEDS_OVERRIDE,          // verdict is not READY; approval needs override=true plus a reason
    BLOCKED_INVALID_NOTES,   // notes reference PRs that don't exist in the evidence; no override, make a new draft
    CLOSED                   // not PENDING, or expired
}
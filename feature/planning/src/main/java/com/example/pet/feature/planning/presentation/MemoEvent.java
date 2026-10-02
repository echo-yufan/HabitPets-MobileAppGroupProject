package com.example.pet.feature.planning.presentation;

/** A message consumed once, including across configuration changes. */
public final class MemoEvent {
    private final String message;
    public final long operationId;
    public final boolean successful;
    private boolean consumed;

    public MemoEvent(String message) { this(message, 0, false); }

    public MemoEvent(String message, long operationId, boolean successful) {
        this.message = message;
        this.operationId = operationId;
        this.successful = successful;
    }

    public String message() { return message; }

    public MemoEvent consumeResult() {
        if (consumed) return null;
        consumed = true;
        return this;
    }

    public String consume() {
        return consumeResult() == null ? null : message;
    }
}

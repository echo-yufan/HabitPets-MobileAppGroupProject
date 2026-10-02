package com.example.pet.feature.chat.domain.model;

/** Immutable persisted message. A retry keeps the same message id. */
public final class ChatMessage {
    public enum Status { SENDING, SENT, FAILED }
    public final String id;
    public final String sessionId;
    public final String role;
    public final String content;
    public final Status status;
    public final String error;

    public ChatMessage(String id, String sessionId, String role, String content,
                       Status status, String error) {
        this.id = id;
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.status = status;
        this.error = error;
    }

    public ChatMessage withStatus(Status status, String error) {
        return new ChatMessage(id, sessionId, role, content, status, error);
    }
}

package com.example.pet.feature.chat.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A snapshot shared by persistence and presentation; callers cannot mutate history. */
public final class ChatState {
    public final String sessionId;
    public final String systemPrompt;
    public final List<ChatMessage> messages;
    public final boolean requesting;

    public ChatState(String sessionId, String systemPrompt, List<ChatMessage> messages,
                     boolean requesting) {
        this.sessionId = sessionId;
        this.systemPrompt = systemPrompt;
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        this.requesting = requesting;
    }

    /** Only the latest failed turn can be retried, preserving chronological context. */
    public String retryMessageId() {
        if (requesting || messages.isEmpty()) return null;
        ChatMessage last = messages.get(messages.size() - 1);
        return "user".equals(last.role) && last.status == ChatMessage.Status.FAILED
                ? last.id : null;
    }
}

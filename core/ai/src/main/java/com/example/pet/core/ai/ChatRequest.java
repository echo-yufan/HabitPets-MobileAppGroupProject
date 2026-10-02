package com.example.pet.core.ai;

/** Cancellation handle without exposing the HTTP implementation to features. */
public interface ChatRequest {
    void cancel();
}

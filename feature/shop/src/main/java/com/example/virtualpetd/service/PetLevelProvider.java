package com.example.virtualpetd.service;

/** Read-only bridge to C's authoritative pet level. Called on a worker thread. */
public interface PetLevelProvider {
    int getLevel(String userId);
}

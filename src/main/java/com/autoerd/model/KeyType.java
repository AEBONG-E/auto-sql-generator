package com.autoerd.model;

public enum KeyType {
    PRI, MUL, UNI, NONE;

    public static KeyType from(String raw) {
        if (raw == null || raw.isBlank()) return NONE;
        return switch (raw.trim().toUpperCase()) {
            case "PRI" -> PRI;
            case "MUL" -> MUL;
            case "UNI" -> UNI;
            default -> NONE;
        };
    }
}

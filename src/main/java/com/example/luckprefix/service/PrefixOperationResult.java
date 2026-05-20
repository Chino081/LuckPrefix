package com.example.luckprefix.service;

public record PrefixOperationResult(boolean success, String reason) {
    public static PrefixOperationResult ok() {
        return new PrefixOperationResult(true, "");
    }

    public static PrefixOperationResult fail(String reason) {
        return new PrefixOperationResult(false, reason);
    }
}

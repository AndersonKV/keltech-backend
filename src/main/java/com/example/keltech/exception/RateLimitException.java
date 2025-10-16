package com.example.keltech.exception;

public class RateLimitException extends Exception {
    public RateLimitException(String message) {
        super(message);
    }
}

package com.example.keltech.exception;

public class HttpErrorException extends Exception {
    private final int statusCode;

    public HttpErrorException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

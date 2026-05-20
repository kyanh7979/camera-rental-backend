package com.camerarental.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AIServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public AIServiceException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public AIServiceException(String message, HttpStatus status) {
        this(message, status, null);
    }

    // Factory methods for common cases
    public static AIServiceException rateLimitExceeded() {
        return new AIServiceException(
                "Gemini API rate limit exceeded. Please wait before trying again.",
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT"
        );
    }

    public static AIServiceException quotaExceeded() {
        return new AIServiceException(
                "Gemini API quota exceeded. Please check your API plan.",
                HttpStatus.TOO_MANY_REQUESTS,
                "QUOTA_EXCEEDED"
        );
    }

    public static AIServiceException invalidApiKey() {
        return new AIServiceException(
                "Invalid Gemini API key. Please contact administrator.",
                HttpStatus.UNAUTHORIZED,
                "INVALID_API_KEY"
        );
    }

    public static AIServiceException serviceUnavailable(String reason) {
        return new AIServiceException(
                "AI service unavailable: " + reason,
                HttpStatus.SERVICE_UNAVAILABLE,
                "SERVICE_UNAVAILABLE"
        );
    }

    public static AIServiceException blockedContent() {
        return new AIServiceException(
                "Content blocked by safety filters.",
                HttpStatus.BAD_REQUEST,
                "BLOCKED_CONTENT"
        );
    }

    public static AIServiceException invalidResponse(String reason) {
        return new AIServiceException(
                "Invalid AI response: " + reason,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INVALID_RESPONSE"
        );
    }

    public static AIServiceException notConfigured() {
        return new AIServiceException(
                "AI service not configured.",
                HttpStatus.SERVICE_UNAVAILABLE,
                "NOT_CONFIGURED"
        );
    }
}

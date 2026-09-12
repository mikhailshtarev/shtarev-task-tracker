package ru.shatrev.auth.exception;

/** Бизнес-ошибка с кодом из контракта API (раздел 4.1). */
public class ApiException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final Object details;

    public ApiException(String code, int httpStatus, String message) {
        this(code, httpStatus, message, null);
    }

    public ApiException(String code, int httpStatus, String message, Object details) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Object getDetails() {
        return details;
    }

    public static ApiException validation(String message, Object details) {
        return new ApiException("VALIDATION_ERROR", 400, message, details);
    }

    public static ApiException unauthorized() {
        return new ApiException("UNAUTHORIZED", 401, "Требуется авторизация");
    }
}

package ru.shatrev.auth.exception;

/** Тело ошибки в едином формате раздела 4.1. */
public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, Object details) {
    }

    public static ErrorResponse of(String code, String message, Object details) {
        return new ErrorResponse(new ErrorBody(code, message, details));
    }
}

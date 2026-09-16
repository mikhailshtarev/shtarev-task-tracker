package ru.shatrev.tasks.branch;

import org.springframework.http.HttpStatus;

import java.util.List;

public class ApiFailure extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<ApiError.Detail> details;

    private ApiFailure(HttpStatus status, String code, String message, List<ApiError.Detail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public static ApiFailure validation(String field, String message) {
        return new ApiFailure(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Проверьте введённые данные",
                List.of(new ApiError.Detail(field, message)));
    }

    public static ApiFailure notFound() {
        return new ApiFailure(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ветка не найдена или недоступна", null);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public List<ApiError.Detail> details() { return details; }
}

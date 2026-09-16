package ru.shatrev.tasks.branch;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BranchErrorHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidInput(MethodArgumentNotValidException ignored) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("VALIDATION_ERROR",
                "Проверьте введённые данные", java.util.List.of(new ApiError.Detail("name", "Укажите название ветки"))));
    }

    @ExceptionHandler(ApiFailure.class)
    public ResponseEntity<ApiError> failure(ApiFailure failure) {
        return ResponseEntity.status(failure.status())
                .body(ApiError.of(failure.code(), failure.getMessage(), failure.details()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformed(HttpMessageNotReadableException ignored) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("VALIDATION_ERROR",
                "Проверьте введённые данные", java.util.List.of(new ApiError.Detail("body", "Некорректное тело запроса"))));
    }
}

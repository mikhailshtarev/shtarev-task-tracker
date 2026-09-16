package ru.shatrev.tasks.branch;

import java.util.List;

public record ApiError(ErrorBody error) {

    public record ErrorBody(String code, String message, List<Detail> details) {
    }

    public record Detail(String field, String message) {
    }

    public static ApiError of(String code, String message, List<Detail> details) {
        return new ApiError(new ErrorBody(code, message, details));
    }
}

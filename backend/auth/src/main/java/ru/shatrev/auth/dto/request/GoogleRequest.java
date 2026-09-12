package ru.shatrev.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleRequest(
        @NotBlank(message = "Укажите код авторизации")
        String code
) {
}

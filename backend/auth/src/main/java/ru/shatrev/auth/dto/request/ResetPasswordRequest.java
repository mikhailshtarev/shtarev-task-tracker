package ru.shatrev.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import ru.shatrev.auth.validation.ValidPassword;

public record ResetPasswordRequest(
        @NotBlank(message = "Укажите токен сброса")
        String token,

        @ValidPassword
        String password
) {
}

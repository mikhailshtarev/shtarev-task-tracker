package ru.shatrev.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.shatrev.auth.validation.ValidPassword;

public record RegisterRequest(
        @NotBlank(message = "Укажите email")
        @Pattern(regexp = "^[^@]+@[^@]+\\.[^@]+$", message = "Некорректный формат email")
        @Size(min = 5, max = 254, message = "Email должен содержать от 5 до 254 символов")
        String email,

        @ValidPassword
        String password
) {
}

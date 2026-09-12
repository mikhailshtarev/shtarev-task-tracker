package ru.shatrev.auth.dto.response;

import java.util.UUID;

public record MeResponse(UUID id, String email, String name) {
}

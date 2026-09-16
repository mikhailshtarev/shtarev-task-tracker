package ru.shatrev.tasks.branch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

public record BranchCursor(Instant createdAt, UUID id) {

    public String encode(String query) {
        String payload = "v1|" + createdAt + "|" + id + "|" + queryHash(query);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static BranchCursor decode(String token, String query) {
        if (token == null) return null;
        if (token.isBlank() || token.length() > 512 || !token.matches("[A-Za-z0-9_-]+")) {
            throw invalid();
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 4 || !parts[0].equals("v1") || !parts[3].equals(queryHash(query))) {
                throw invalid();
            }
            Instant createdAt = Instant.parse(parts[1]);
            UUID id = UUID.fromString(parts[2]);
            if (!id.toString().equals(parts[2]) || !new BranchCursor(createdAt, id).encode(query).equals(token)) {
                throw invalid();
            }
            return new BranchCursor(createdAt, id);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static String queryHash(String query) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(query.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiFailure invalid() {
        return ApiFailure.validation("cursor", "Некорректный курсор страницы");
    }
}

package ru.shatrev.tasks.workplan;

import ru.shatrev.tasks.branch.ApiFailure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

public record WorkPlanCursor(UUID branchId, Instant createdAt, UUID id) {
    public String encode(String query) {
        String payload = "v1|" + branchId + "|" + createdAt + "|" + id + "|" + queryHash(query);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static WorkPlanCursor decode(String token, UUID branchId, String query) {
        if (token == null) return null;
        if (token.isBlank() || token.length() > 512 || !token.matches("[A-Za-z0-9_-]+")) throw invalid();
        try {
            String payload = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 5 || !parts[0].equals("v1") || !parts[4].equals(queryHash(query))) throw invalid();
            UUID parsedBranch = UUID.fromString(parts[1]);
            Instant createdAt = Instant.parse(parts[2]);
            UUID id = UUID.fromString(parts[3]);
            WorkPlanCursor cursor = new WorkPlanCursor(parsedBranch, createdAt, id);
            if (!branchId.equals(parsedBranch) || !cursor.encode(query).equals(token)) throw invalid();
            return cursor;
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static String queryHash(String query) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(query.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiFailure invalid() {
        return ApiFailure.validation("cursor", "Некорректный курсор страницы");
    }
}

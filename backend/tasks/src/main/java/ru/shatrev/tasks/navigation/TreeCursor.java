package ru.shatrev.tasks.navigation;

import ru.shatrev.tasks.branch.ApiFailure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

record TreeCursor(int rank, Instant createdAt, UUID id) {
    String encode(UUID userId, String parentType, UUID parentId) {
        String payload = "v1|" + rank + "|" + createdAt + "|" + id + "|" + contextHash(userId, parentType, parentId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    static TreeCursor decode(String token, UUID userId, String parentType, UUID parentId) {
        if (token == null) return null;
        if (token.isBlank() || token.length() > 512 || !token.matches("[A-Za-z0-9_-]+")) throw invalid();
        try {
            String payload = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 5 || !parts[0].equals("v1") || !parts[4].equals(contextHash(userId, parentType, parentId))) {
                throw invalid();
            }
            int rank = Integer.parseInt(parts[1]);
            if (rank < 0 || rank > 1) throw invalid();
            TreeCursor cursor = new TreeCursor(rank, Instant.parse(parts[2]), UUID.fromString(parts[3]));
            if (!cursor.encode(userId, parentType, parentId).equals(token)) throw invalid();
            return cursor;
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static String contextHash(UUID userId, String parentType, UUID parentId) {
        String context = userId + "|" + (parentType == null ? "root" : parentType) + "|" + parentId;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(context.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiFailure invalid() {
        return ApiFailure.validation("cursor", "Некорректный курсор страницы");
    }
}

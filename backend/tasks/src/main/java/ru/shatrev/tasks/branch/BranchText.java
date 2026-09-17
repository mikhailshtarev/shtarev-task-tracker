package ru.shatrev.tasks.branch;

import java.text.Normalizer;

public final class BranchText {
    private BranchText() {
    }

    public static String name(String raw) {
        if (raw == null) {
            throw ApiFailure.validation("name", "Укажите название ветки");
        }
        String value = normalize(raw);
        int count = value.codePointCount(0, value.length());
        if (count < 2 || count > 100) {
            throw ApiFailure.validation("name", "Название должно содержать от 2 до 100 символов");
        }
        return value;
    }

    public static String query(String raw) {
        String value = raw == null ? "" : normalize(raw);
        if (value.codePointCount(0, value.length()) > 100) {
            throw ApiFailure.validation("q", "Поисковый запрос должен содержать не более 100 символов");
        }
        return value;
    }

    public static String normalize(String raw) {
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC);
        int start = 0;
        int end = value.length();
        while (start < end) {
            int point = value.codePointAt(start);
            if (!edgeWhitespace(point)) break;
            start += Character.charCount(point);
        }
        while (end > start) {
            int point = value.codePointBefore(end);
            if (!edgeWhitespace(point)) break;
            end -= Character.charCount(point);
        }
        return value.substring(start, end);
    }

    private static boolean edgeWhitespace(int point) {
        return (point >= 0x0009 && point <= 0x000D) || point == 0x0020 || point == 0x00A0
                || point == 0x1680 || (point >= 0x2000 && point <= 0x200A)
                || point == 0x2028 || point == 0x2029 || point == 0x202F
                || point == 0x205F || point == 0x3000 || point == 0xFEFF;
    }
}

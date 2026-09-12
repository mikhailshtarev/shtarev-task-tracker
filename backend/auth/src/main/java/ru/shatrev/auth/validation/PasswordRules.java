package ru.shatrev.auth.validation;

/** Правила валидации пароля (раздел 7.2 спецификации). */
public final class PasswordRules {

    /** 8–128 символов, минимум одна заглавная, одна строчная буква и одна цифра. */
    public static final String PATTERN = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{8,128}$";

    public static final String MESSAGE =
            "Пароль должен содержать от 8 до 128 символов, минимум одну заглавную букву, строчную букву и цифру";

    private PasswordRules() {
    }

    public static boolean isValid(String password) {
        return password != null && password.matches(PATTERN);
    }
}

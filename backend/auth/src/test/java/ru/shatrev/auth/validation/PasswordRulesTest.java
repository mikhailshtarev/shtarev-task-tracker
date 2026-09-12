package ru.shatrev.auth.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** B-03: валидация пароля — без заглавной / без цифры / &lt;8 / &gt;128. */
class PasswordRulesTest {

    @Test
    void acceptsValidPassword() {
        assertThat(PasswordRules.isValid("SecurePass1")).isTrue();
        assertThat(PasswordRules.isValid("A1b" + "x".repeat(125))).isTrue(); // ровно 128
    }

    @Test
    void rejectsMissingUppercase() {
        assertThat(PasswordRules.isValid("securepass1")).isFalse();
    }

    @Test
    void rejectsMissingDigit() {
        assertThat(PasswordRules.isValid("SecurePass")).isFalse();
    }

    @Test
    void rejectsMissingLowercase() {
        assertThat(PasswordRules.isValid("SECUREPASS1")).isFalse();
    }

    @Test
    void rejectsTooShort() {
        assertThat(PasswordRules.isValid("A1b2C3d")).isFalse(); // 7 символов
    }

    @Test
    void rejectsTooLong() {
        assertThat(PasswordRules.isValid("A1b" + "x".repeat(126))).isFalse(); // 129 символов
    }

    @Test
    void rejectsNullAndEmpty() {
        assertThat(PasswordRules.isValid(null)).isFalse();
        assertThat(PasswordRules.isValid("")).isFalse();
    }
}

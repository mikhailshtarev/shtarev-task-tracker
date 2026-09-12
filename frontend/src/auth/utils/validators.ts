// Клиентские правила валидации (frontend-auth-integration.md, раздел 2.1).
// Клиентская валидация не заменяет серверную.

export const EMAIL_MIN_LENGTH = 5;
export const EMAIL_MAX_LENGTH = 254;
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 128;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateEmail(value: string): string | null {
  const email = value.trim();
  if (email.length < EMAIL_MIN_LENGTH || email.length > EMAIL_MAX_LENGTH) {
    return `Email должен содержать от ${EMAIL_MIN_LENGTH} до ${EMAIL_MAX_LENGTH} символов`;
  }
  if (!EMAIL_PATTERN.test(email)) {
    return 'Неверный формат email';
  }
  return null;
}

export function validatePassword(value: string): string | null {
  if (value.length < PASSWORD_MIN_LENGTH || value.length > PASSWORD_MAX_LENGTH) {
    return `Пароль должен содержать от ${PASSWORD_MIN_LENGTH} до ${PASSWORD_MAX_LENGTH} символов`;
  }
  if (!/[A-ZА-Я]/.test(value)) {
    return 'Пароль должен содержать хотя бы одну заглавную букву';
  }
  if (!/[a-zа-я]/.test(value)) {
    return 'Пароль должен содержать хотя бы одну строчную букву';
  }
  if (!/\d/.test(value)) {
    return 'Пароль должен содержать хотя бы одну цифру';
  }
  return null;
}

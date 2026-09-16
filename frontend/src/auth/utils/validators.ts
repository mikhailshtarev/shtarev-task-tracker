// Клиентские правила валидации (frontend-auth-integration.md, раздел 2.1).
// Клиентская валидация не заменяет серверную.

export const EMAIL_MIN_LENGTH = 5;
export const EMAIL_MAX_LENGTH = 254;
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 128;
export const PROFILE_NAME_MAX_LENGTH = 100;

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

export function validateProfileName(value: string): string | null {
  if (value.trim().length > PROFILE_NAME_MAX_LENGTH) {
    return `Имя должно содержать не более ${PROFILE_NAME_MAX_LENGTH} символов`;
  }
  return null;
}

export function validatePomodoroMinutes(value: number): string | null {
  if (!Number.isInteger(value) || value < 5 || value > 120 || value % 5 !== 0) {
    return 'Укажите целое число от 5 до 120 с шагом 5 минут';
  }
  return null;
}

export function validateBudgetHourCost(value: number): string | null {
  if (!Number.isInteger(value) || value < 1 || value > 1000) {
    return 'Укажите целое число от 1 до 1000';
  }
  return null;
}

export function validateEstimationUnit(value: string): string | null {
  if (value !== 'hours' && value !== 'pomodoros') {
    return 'Выберите единицу оценки';
  }
  return null;
}

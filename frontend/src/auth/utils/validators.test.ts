import { describe, expect, it } from 'vitest';
import {
  validateBudgetHourCost,
  validateEmail,
  validateEstimationUnit,
  validatePassword,
  validatePomodoroMinutes,
  validateProfileName,
} from './validators';

describe('validateEmail', () => {
  it('F-01: валидный email — ошибок нет', () => {
    expect(validateEmail('user@example.com')).toBeNull();
  });

  it('невалидный формат — есть ошибка', () => {
    expect(validateEmail('not-an-email')).not.toBeNull();
  });

  it('слишком короткий email — есть ошибка', () => {
    expect(validateEmail('a@b')).not.toBeNull();
  });
});

describe('validatePassword', () => {
  it('F-01: валидный пароль — ошибок нет', () => {
    expect(validatePassword('SecurePass1')).toBeNull();
  });

  it('F-02: пароль без заглавной буквы — понятное сообщение', () => {
    const error = validatePassword('securepass1');
    expect(error).toBe('Пароль должен содержать хотя бы одну заглавную букву');
  });

  it('F-02: пароль без цифры — понятное сообщение', () => {
    const error = validatePassword('SecurePass');
    expect(error).toBe('Пароль должен содержать хотя бы одну цифру');
  });

  it('F-02: пароль без строчной буквы — понятное сообщение', () => {
    const error = validatePassword('SECUREPASS1');
    expect(error).toBe('Пароль должен содержать хотя бы одну строчную букву');
  });

  it('F-02: пароль короче 8 символов — понятное сообщение', () => {
    const error = validatePassword('Secure1');
    expect(error).toBe('Пароль должен содержать от 8 до 128 символов');
  });

  it('F-02: пароль длиннее 128 символов — понятное сообщение', () => {
    const error = validatePassword(`Secure1${'a'.repeat(122)}`);
    expect(error).toBe('Пароль должен содержать от 8 до 128 символов');
  });
});

describe('F-3 validators', () => {
  it('FE-01: проверяет длительность помидора и шаг 5 минут', () => {
    expect(validatePomodoroMinutes(5)).toBeNull();
    expect(validatePomodoroMinutes(120)).toBeNull();
    expect(validatePomodoroMinutes(0)).not.toBeNull();
    expect(validatePomodoroMinutes(121)).not.toBeNull();
    expect(validatePomodoroMinutes(26)).not.toBeNull();
    expect(validatePomodoroMinutes(25.5)).not.toBeNull();
  });

  it('FE-01: проверяет диапазон стоимости часа', () => {
    expect(validateBudgetHourCost(1)).toBeNull();
    expect(validateBudgetHourCost(1000)).toBeNull();
    expect(validateBudgetHourCost(1001)).not.toBeNull();
    expect(validateBudgetHourCost(1.5)).not.toBeNull();
  });

  it('FE-01: принимает только поддерживаемые единицы оценки', () => {
    expect(validateEstimationUnit('hours')).toBeNull();
    expect(validateEstimationUnit('pomodoros')).toBeNull();
    expect(validateEstimationUnit('minutes')).not.toBeNull();
  });

  it('FE-02: имя допускает от 0 до 100 символов', () => {
    expect(validateProfileName('')).toBeNull();
    expect(validateProfileName('а'.repeat(100))).toBeNull();
    expect(validateProfileName('а'.repeat(101))).not.toBeNull();
  });
});

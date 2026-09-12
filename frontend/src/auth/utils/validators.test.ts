import { describe, expect, it } from 'vitest';
import {
  validateEmail,
  validatePassword,
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

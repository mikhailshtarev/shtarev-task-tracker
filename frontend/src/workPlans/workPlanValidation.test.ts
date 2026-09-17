import { describe, expect, it } from 'vitest';
import {
  normalizeWorkPlanText,
  validateWorkPlanName,
  validateWorkPlanQuery,
} from './workPlanValidation';

describe('workPlanValidation', () => {
  it('FE-10: нормализует NFC и удаляет только допустимые краевые пробелы', () => {
    expect(normalizeWorkPlanText('\u00A0\u2003e\u0301\u00A0')).toBe('é');
    expect(normalizeWorkPlanText('Работа\u00A0и дом')).toBe('Работа\u00A0и дом');
  });

  it('FE-10: считает emoji кодовыми точками', () => {
    expect(validateWorkPlanName('😀'.repeat(150))).toBeNull();
    expect(validateWorkPlanName('😀'.repeat(151))).not.toBeNull();
    expect(validateWorkPlanQuery('😀'.repeat(151))).not.toBeNull();
  });

  it('FE-03: отклоняет название короче двух символов после нормализации', () => {
    expect(validateWorkPlanName(' \u00A0A\u2003')).not.toBeNull();
    expect(validateWorkPlanName(' План ')).toBeNull();
  });
});

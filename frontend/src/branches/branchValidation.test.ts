import { describe, expect, it } from 'vitest';
import {
  countCodePoints,
  normalizeBranchText,
  validateBranchName,
  validateBranchQuery,
} from './branchValidation';

describe('branchValidation', () => {
  it('FE-14: нормализует NFC и удаляет только указанные краевые пробелы', () => {
    expect(normalizeBranchText('\u00A0\u2003e\u0301\u00A0')).toBe('é');
    expect(normalizeBranchText('Работа\u00A0и дом')).toBe('Работа\u00A0и дом');
  });

  it('FE-14: считает кодовые точки, а не UTF-16 единицы', () => {
    expect(countCodePoints('😀')).toBe(1);
    expect(validateBranchName('😀'.repeat(100))).toBeNull();
    expect(validateBranchName('😀'.repeat(101))).not.toBeNull();
    expect(validateBranchQuery('😀'.repeat(101))).not.toBeNull();
  });

  it('FE-03: отклоняет название после нормализации короче двух символов', () => {
    expect(validateBranchName(' \u00A0A\u2003')).not.toBeNull();
    expect(validateBranchName(' Дом ')).toBeNull();
  });
});

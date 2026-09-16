import { BRANCH_STRINGS } from './strings';

export const BRANCH_NAME_MIN_LENGTH = 2;
export const BRANCH_TEXT_MAX_LENGTH = 100;

const EDGE_WHITESPACE = /^[\u0009-\u000D\u0020\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]+|[\u0009-\u000D\u0020\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]+$/gu;

export function normalizeBranchText(value: string): string {
  return value.normalize('NFC').replace(EDGE_WHITESPACE, '');
}

export function countCodePoints(value: string): number {
  return Array.from(value).length;
}

export function validateBranchName(value: string): string | null {
  const length = countCodePoints(normalizeBranchText(value));
  if (length < BRANCH_NAME_MIN_LENGTH || length > BRANCH_TEXT_MAX_LENGTH) {
    return BRANCH_STRINGS.nameLengthError;
  }
  return null;
}

export function validateBranchQuery(value: string): string | null {
  if (countCodePoints(normalizeBranchText(value)) > BRANCH_TEXT_MAX_LENGTH) {
    return BRANCH_STRINGS.queryLengthError;
  }
  return null;
}

export function isBranchId(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

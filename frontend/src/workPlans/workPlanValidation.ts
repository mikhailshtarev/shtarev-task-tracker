import { countCodePoints, normalizeBranchText } from '../branches/branchValidation';
import { WORK_PLAN_STRINGS } from './strings';

export const WORK_PLAN_NAME_MIN_LENGTH = 2;
export const WORK_PLAN_TEXT_MAX_LENGTH = 150;

export function normalizeWorkPlanText(value: string): string {
  return normalizeBranchText(value);
}

export function validateWorkPlanName(value: string): string | null {
  const length = countCodePoints(normalizeWorkPlanText(value));
  if (length < WORK_PLAN_NAME_MIN_LENGTH || length > WORK_PLAN_TEXT_MAX_LENGTH) {
    return WORK_PLAN_STRINGS.nameLengthError;
  }
  return null;
}

export function validateWorkPlanQuery(value: string): string | null {
  if (countCodePoints(normalizeWorkPlanText(value)) > WORK_PLAN_TEXT_MAX_LENGTH) {
    return WORK_PLAN_STRINGS.queryLengthError;
  }
  return null;
}

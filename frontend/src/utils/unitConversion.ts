import type { EstimationUnit } from '../auth/services/authService';

const MINUTES_IN_HOUR = 60;
const STORAGE_STEP_MINUTES = 5;

function assertConversionInput(value: number, pomodoroMinutes: number): void {
  if (!Number.isFinite(value) || value < 0) {
    throw new RangeError('Значение времени должно быть неотрицательным числом');
  }
  if (!Number.isInteger(pomodoroMinutes) || pomodoroMinutes <= 0) {
    throw new RangeError('Длительность помидора должна быть положительным целым числом');
  }
}

function roundHoursToStorageStep(hours: number): number {
  const minutes = Math.round((hours * MINUTES_IN_HOUR) / STORAGE_STEP_MINUTES)
    * STORAGE_STEP_MINUTES;
  return minutes / MINUTES_IN_HOUR;
}

export function hoursToDisplay(
  hours: number,
  unit: EstimationUnit,
  pomodoroMinutes: number,
): number {
  assertConversionInput(hours, pomodoroMinutes);
  if (unit === 'pomodoros') {
    return Math.round((hours * MINUTES_IN_HOUR) / pomodoroMinutes);
  }
  return roundHoursToStorageStep(hours);
}

export function displayToHours(
  value: number,
  unit: EstimationUnit,
  pomodoroMinutes: number,
): number {
  assertConversionInput(value, pomodoroMinutes);
  const hours = unit === 'pomodoros'
    ? (value * pomodoroMinutes) / MINUTES_IN_HOUR
    : value;
  return roundHoursToStorageStep(hours);
}

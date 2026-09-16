import { describe, expect, it } from 'vitest';
import { displayToHours, hoursToDisplay } from './unitConversion';

describe('unitConversion', () => {
  it('FE-03: округляет часы до целого количества помидоров только для отображения', () => {
    expect(hoursToDisplay(1.5, 'pomodoros', 25)).toBe(4);
    expect(hoursToDisplay(2, 'pomodoros', 25)).toBe(5);
  });

  it('FE-03: переводит помидоры обратно в часы с точностью до 5 минут', () => {
    expect(displayToHours(4, 'pomodoros', 25)).toBeCloseTo(5 / 3);
    expect(displayToHours(3, 'pomodoros', 25)).toBe(1.25);
  });

  it('приводит ввод в часах к шагу 5 минут', () => {
    expect(displayToHours(1.52, 'hours', 25)).toBe(1.5);
    expect(hoursToDisplay(1.52, 'hours', 25)).toBe(1.5);
  });
});

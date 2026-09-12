import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from './tokenManager';

describe('tokenManager', () => {
  beforeEach(() => {
    clearAccessToken();
  });

  afterEach(() => {
    clearAccessToken();
  });

  it('F-03: set → get → clear', () => {
    expect(getAccessToken()).toBeNull();

    setAccessToken('token-1');
    expect(getAccessToken()).toBe('token-1');

    clearAccessToken();
    expect(getAccessToken()).toBeNull();
  });

  it('F-03: токен не попадает в localStorage и sessionStorage', () => {
    setAccessToken('secret-token');

    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(sessionStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});

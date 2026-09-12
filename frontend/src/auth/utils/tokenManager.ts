// Access token хранится только в памяти (см. frontend-auth-integration.md, раздел 3.1).
// Запрещено хранить его в localStorage, sessionStorage или cookie.
let accessToken: string | null = null;

export function setAccessToken(token: string): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function clearAccessToken(): void {
  accessToken = null;
}

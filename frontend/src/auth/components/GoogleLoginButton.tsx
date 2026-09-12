const GOOGLE_AUTH_URL = 'https://accounts.google.com/o/oauth2/v2/auth';

// Ключ, общий для GoogleLoginButton и GoogleCallbackPage (раздел 2.9 контракта).
export const GOOGLE_STATE_KEY = 'google_oauth_state';

export function generateOAuthState(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

export function buildGoogleAuthUrl(clientId: string, state: string): string {
  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: `${window.location.origin}/google-callback`,
    response_type: 'code',
    scope: 'openid email profile',
    state,
  });
  return `${GOOGLE_AUTH_URL}?${params}`;
}

export function GoogleLoginButton() {
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

  const handleClick = () => {
    if (!clientId) {
      // Без client_id вход через Google невозможен.
      window.alert('Вход через Google не настроен');
      return;
    }
    const state = generateOAuthState();
    sessionStorage.setItem(GOOGLE_STATE_KEY, state);
    window.location.assign(buildGoogleAuthUrl(clientId, state));
  };

  return (
    <button type="button" className="btn btn-secondary btn-block" onClick={handleClick}>
      Войти через Google
    </button>
  );
}

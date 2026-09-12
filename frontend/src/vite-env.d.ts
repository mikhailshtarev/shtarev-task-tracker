/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Google OAuth client id для кнопки «Войти через Google». */
  readonly VITE_GOOGLE_CLIENT_ID?: string;
}

declare module '*.css' {
  const content: Record<string, string>;
  export default content;
}

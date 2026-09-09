import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';

type Theme = 'dark' | 'light';
type Mode = 'minimal' | 'premium';

interface ThemeContextValue {
  theme: Theme;
  mode: Mode;
  setTheme: (theme: Theme) => void;
  setMode: (mode: Mode) => void;
  isPremium: boolean;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => {
    return (localStorage.getItem('theme') as Theme) || 'dark';
  });

  const [mode, setModeState] = useState<Mode>(() => {
    return (localStorage.getItem('mode') as Mode) || 'minimal';
  });

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
  }, [theme]);

  useEffect(() => {
    document.documentElement.setAttribute('data-mode', mode);
    localStorage.setItem('mode', mode);
  }, [mode]);

  const setTheme = (newTheme: Theme) => setThemeState(newTheme);

  const setMode = (newMode: Mode) => setModeState(newMode);

  return (
    <ThemeContext.Provider
      value={{ theme, mode, setTheme, setMode, isPremium: false }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within ThemeProvider');
  }
  return context;
}

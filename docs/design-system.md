# Дизайн-система Task Tracker

## 1. Обзор

Документ определяет визуальную дизайн-систему проекта. Проект поддерживает **две темы** (тёмная/светлая) и **два режима дизайна** (минималистичный/премиум).

| Параметр | Значение |
| --- | --- |
| Тема по умолчанию | Тёмная (`dark`) |
| Режим по умолчанию | Минималистичный (`minimal`) |
| Шрифты | Roboto (основной), Open Sans (альтернатива) |
| Переключение тем | Настройки профиля |
| Переключение режима | Настройки профиля (доступно при активной подписке) |

---

## 2. Тема (Theme)

### 2.1. Тёмная тема (Dark) — по умолчанию

| Роль | Цвет | HEX |
| --- | --- | --- |
| Фон страницы | Тёмно-серый | `#0D0D0D` |
| Фон карточек/панелей | Серый | `#1A1A1A` |
| Фон инпутов | Светло-серый | `#2A2A2A` |
| Текст основной | Белый | `#FFFFFF` |
| Текст вторичный | Серый | `#A0A0A0` |
| Границы | Тёмно-серый | `#333333` |
| Акцент основной | Синий | `#4A90D9` |
| Акцент hover | Светло-синий | `#5BA0E9` |
| Успех | Зелёный | `#4CAF50` |
| Ошибка | Красный | `#F44336` |
| Предупреждение | Жёлтый | `#FFC107` |
| Инфо | Голубой | `#2196F3` |

### 2.2. Светлая тема (Light)

| Роль | Цвет | HEX |
| --- | --- | --- |
| Фон страницы | Светло-серый | `#F5F5F5` |
| Фон карточек/панелей | Белый | `#FFFFFF` |
| Фон инпутов | Светло-серый | `#F0F0F0` |
| Текст основной | Тёмно-серый | `#212121` |
| Текст вторичный | Серый | `#757575` |
| Границы | Светло-серый | `#E0E0E0` |
| Акцент основной | Синий | `#1976D2` |
| Акцент hover | Тёмно-синий | `#1565C0` |
| Успех | Зелёный | `#388E3C` |
| Ошибка | Красный | `#D32F2F` |
| Предупреждение | Янтарный | `#FFA000` |
| Инфо | Синий | `#1976D2` |

---

## 3. Режимы дизайна

### 3.1. Минималистичный режим (Minimal) — по умолчанию

**Философия:** Чистота, функциональность, минимум визуального шума.

| Элемент | Стиль |
| --- | --- |
| Границы карточек | Тонкие (1px), без скруглений или минимальные (4px) |
| Тени | Минимальные или отсутствуют |
| Скругления | 4px (кнопки, инпуты, карточки) |
| Градиенты | Отсутствуют |
| Анимации | Минимальные (150ms, ease-out) |
| Иконки | Line-style (тонкие), монохромные |
| Заголовки | Жирный шрифт, без декоративных элементов |
| Разделители | Тонкие линии (1px) |

### 3.2. Премиум режим (Premium) — с подпиской

**Философия:** Яркий, энергичный, с элементами игровой эстетики.

| Элемент | Стиль |
| --- | --- |
| Границы карточек | 1px с неоновым свечением при hover |
| Тени | Цветные тени (glow-эффект) |
| Скругления | 8px (кнопки, инпуты, карточки) |
| Градиенты | Основные акценты — градиенты (см. ниже) |
| Анимации | Плавные (200-300ms), с easing |
| Иконки | Можно использовать цветные/заполненные |
| Заголовки | Градиентный текст или с декоративной линией |
| Разделители | Градиентные линии |

#### 3.2.1. Премиум-градиенты

**Основной градиент (акценты, кнопки):**
```
linear-gradient(135deg, #667eea 0%, #764ba2 100%)
```

**Неоновый акцент (свечение, hover):**
```
linear-gradient(90deg, #00f5a0, #00d9f5)
```

**Градиент для заголовков:**
```
linear-gradient(90deg, #f97316, #ec4899, #8b5cf6)
```

**Фоновый градиент (hero-секции):**
```
linear-gradient(135deg, #0f0c29 0%, #302b63 50%, #24243e 100%)
```

#### 3.2.2. Премиум-эффекты

**Glow-эффект (неоновое свечение):**
```css
box-shadow: 0 0 10px rgba(102, 126, 234, 0.5),
            0 0 20px rgba(102, 126, 234, 0.3),
            0 0 40px rgba(102, 126, 234, 0.1);
```

**Текст с градиентом:**
```css
background: linear-gradient(90deg, #f97316, #ec4899, #8b5cf6);
-webkit-background-clip: text;
-webkit-text-fill-color: transparent;
background-clip: text;
```

---

## 4. Типографика

### 4.1. Шрифты

| Роль | Шрифт | Вес | Размер |
| --- | --- | --- | --- |
| Заголовки H1 | Roboto | 700 (Bold) | 32px |
| Заголовки H2 | Roboto | 700 (Bold) | 24px |
| Заголовки H3 | Roboto | 600 (SemiBold) | 20px |
| Основной текст | Roboto | 400 (Regular) | 14px |
| Мелкий текст | Roboto | 400 (Regular) | 12px |
| Кнопки | Roboto | 500 (Medium) | 14px |
| Метки/лейблы | Roboto | 500 (Medium) | 12px |

### 4.2. Межстрочные интервалы

| Элемент | Line-height |
| --- | --- |
| Заголовки | 1.2 |
| Основной текст | 1.5 |
| Кнопки | 1 |

### 4.3. Подключение шрифтов

```html
<!-- index.html -->
<link rel="preconnect" href="https://fonts.googleapis.com" />
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
<link
  href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&display=swap"
  rel="stylesheet"
/>
```

```css
/* styles.css */
:root {
  --font-primary: 'Roboto', sans-serif;
}

body {
  font-family: var(--font-primary);
}
```

---

## 5. Дизайн-токены (CSS Custom Properties)

### 5.1. Базовые токены

```css
/* themes/dark.css */
[data-theme='dark'] {
  --bg-primary: #0D0D0D;
  --bg-secondary: #1A1A1A;
  --bg-input: #2A2A2A;
  --text-primary: #FFFFFF;
  --text-secondary: #A0A0A0;
  --border-color: #333333;
  --accent-primary: #4A90D9;
  --accent-hover: #5BA0E9;
  --success: #4CAF50;
  --error: #F44336;
  --warning: #FFC107;
  --info: #2196F3;
}

/* themes/light.css */
[data-theme='light'] {
  --bg-primary: #F5F5F5;
  --bg-secondary: #FFFFFF;
  --bg-input: #F0F0F0;
  --text-primary: #212121;
  --text-secondary: #757575;
  --border-color: #E0E0E0;
  --accent-primary: #1976D2;
  --accent-hover: #1565C0;
  --success: #388E3C;
  --error: #D32F2F;
  --warning: #FFA000;
  --info: #1976D2;
}
```

### 5.2. Токены режимов дизайна

```css
/* modes/minimal.css */
[data-mode='minimal'] {
  --border-radius: 4px;
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.1);
  --shadow-md: 0 2px 4px rgba(0, 0, 0, 0.15);
  --transition-speed: 150ms;
  --gradient-primary: none;
  --glow-effect: none;
}

/* modes/premium.css */
[data-mode='premium'] {
  --border-radius: 8px;
  --shadow-sm: 0 2px 8px rgba(102, 126, 234, 0.2);
  --shadow-md: 0 4px 16px rgba(102, 126, 234, 0.3);
  --transition-speed: 250ms;
  --gradient-primary: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  --glow-effect: 0 0 10px rgba(102, 126, 234, 0.5);
}
```

---

## 6. Компоненты

### 6.1. Кнопки

#### Минималистичный режим

| Состояние | Стиль |
| --- | --- |
| Primary | Фон `var(--accent-primary)`, текст белый, без рамки |
| Secondary | Прозрачный фон, border 1px `var(--accent-primary)`, текст `var(--accent-primary)` |
| Danger | Фон `var(--error)`, текст белый |
| Ghost | Прозрачный фон, текст `var(--text-primary)`, hover — лёгкий фон |

#### Премиум режим

| Состояние | Стиль |
| --- | --- |
| Primary | Градиент `var(--gradient-primary)`, текст белый, glow при hover |
| Secondary | Прозрачный фон, border градиентный, glow при hover |
| Danger | Фон `var(--error)`, glow-тень красная при hover |
| Ghost | Прозрачный фон, текст с градиентом при hover |

### 6.2. Карточки

#### Минималистичный режим

- Фон: `var(--bg-secondary)`
- Граница: 1px `var(--border-color)`
- Скругление: 4px
- Тень: отсутствует или минимальная

#### Премиум режим

- Фон: `var(--bg-secondary)`
- Граница: 1px с прозрачным градиентом
- Скругление: 8px
- Тень: цветная glow-тень
- Hover: усиленное свечение

### 6.3. Поля ввода (Inputs)

| Состояние | Минимализм | Премиум |
| --- | --- | --- |
| Default | Фон `var(--bg-input)`, border 1px `var(--border-color)` | Фон `var(--bg-input)`, border 1px `var(--border-color)` |
| Focus | Border `var(--accent-primary)` | Border `var(--accent-primary)`, glow-тень |
| Error | Border `var(--error)` | Border `var(--error)`, glow-тень красная |
| Disabled | Прозрачность 0.5 | Прозрачность 0.5 |

### 6.4. Навигация

#### Минималистичный режим

- Горизонтальная или боковая панель
- Фон: `var(--bg-secondary)`
- Активный элемент: нижняя граница `var(--accent-primary)` или фон
- Без декоративных элементов

#### Премиум режим

- Фон: `var(--bg-secondary)`
- Активный элемент: градиентная нижняя граница или glow-фон
- Hover: лёгкое свечение
- Можно добавить иконки с градиентом

---

## 7. Утилитарные CSS-классы

```css
/* Утилиты */
.glow-primary {
  box-shadow: 0 0 10px rgba(102, 126, 234, 0.5),
              0 0 20px rgba(102, 126, 234, 0.3);
}

.glow-success {
  box-shadow: 0 0 10px rgba(76, 175, 80, 0.5),
              0 0 20px rgba(76, 175, 80, 0.3);
}

.glow-error {
  box-shadow: 0 0 10px rgba(244, 67, 54, 0.5),
              0 0 20px rgba(244, 67, 54, 0.3);
}

.gradient-text {
  background: linear-gradient(90deg, #f97316, #ec4899, #8b5cf6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.gradient-border {
  border: 1px solid transparent;
  background: linear-gradient(var(--bg-secondary), var(--bg-secondary)) padding-box,
              linear-gradient(135deg, #667eea, #764ba2) border-box;
  border-radius: 8px;
}
```

---

## 8. Адаптивность

| Брейкпоинт | Ширина | Описание |
| --- | --- | --- |
| `xs` | < 640px | Мобильные |
| `sm` | ≥ 640px | Планшеты вертикально |
| `md` | ≥ 768px | Планшеты горизонтально |
| `lg` | ≥ 1024px | Десктоп |
| `xl` | ≥ 1280px | Большие экраны |

### 8.1. Правила

- На мобильных: боковая навигация → горизонтальная панель под шапкой
- Карточки: на мобильных — 1 колонка, на десктопе — 2-3
- Таблицы: на мобильных — горизонтальный скролл или карточный вид
- Шрифты: уменьшать на 2px на мобильных

### 8.2. Навигация авторизованной части

- Основные разделы приложения располагаются в постоянной боковой колонке слева: «Дашборд», «Ветки» и последующие рабочие разделы.
- Профиль и настройки находятся в правом верхнем углу шапки рядом с данными текущего пользователя и выходом.
- На мобильных экранах боковая колонка превращается в горизонтальную панель под шапкой; ссылки профиля и настроек остаются в верхней части.

---

## 9. Анимации

| Событие | Минимализм | Премиум |
| --- | --- | --- |
| Hover на кнопке | 150ms, ease-out | 200ms, ease-out + glow |
| Hover на карточке | 150ms, translateY(2px) | 250ms, translateY(-4px) + glow |
| Появление модалки | 200ms, fade-in | 300ms, fade-in + scale(0.95→1) |
| Переход между страницами | 150ms, fade | 250ms, fade + slide |
| Загрузка (спиннер) | Простой круговой спиннер | Спиннер с градиентом |

---

## 10. Иконки

### 10.1. Стиль

| Режим | Стиль иконок |
| --- | --- |
| Минималистичный | Line-style (тонкие), монохромные, `currentColor` |
| Премиум | Можно использовать filled-style, цветные или с градиентом |

### 10.2. Рекомендуемые библиотеки

- **Минималистичный:** Material Icons Outlined, Lucide, Feather Icons
- **Премиум:** Material Icons Round, или кастомные SVG с градиентами

---

## 11. Структура файлов стилей

```
frontend/src/
├── styles/
│   ├── themes/
│   │   ├── dark.css           # Тёмная тема
│   │   └── light.css          # Светлая тема
│   ├── modes/
│   │   ├── minimal.css        # Минималистичный режим
│   │   └── premium.css        # Премиум режим
│   ├── tokens.css             # Дизайн-токены (импортирует темы и режимы)
│   ├── components/
│   │   ├── buttons.css        # Стили кнопок
│   │   ├── cards.css          # Стили карточек
│   │   ├── inputs.css         # Стили полей ввода
│   │   ├── navigation.css     # Стили навигации
│   │   └── modals.css         # Стили модалок
│   ├── utilities.css          # Утилитарные классы
│   ├── animations.css         # Анимации
│   └── globals.css            # Глобальные стили (импорты, body, reset)
├── components/
│   ├── Button.tsx
│   ├── Card.tsx
│   ├── Input.tsx
│   └── ...
└── context/
    └── ThemeContext.tsx       # Контекст темы и режима
```

---

## 12. Контекст темы (пример реализации)

```typescript
// context/ThemeContext.tsx
import { createContext, useContext, useState, useEffect } from 'react';

type Theme = 'dark' | 'light';
type Mode = 'minimal' | 'premium';

interface ThemeContextValue {
  theme: Theme;
  mode: Mode;
  setTheme: (theme: Theme) => void;
  setMode: (mode: Mode) => void;
  isPremium: boolean; // есть ли подписка
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children, isPremium }: {
  children: React.ReactNode;
  isPremium: boolean;
}) {
  const [theme, setThemeState] = useState<Theme>(() => {
    return (localStorage.getItem('theme') as Theme) || 'dark';
  });

  const [mode, setModeState] = useState<Mode>(() => {
    return (localStorage.getItem('mode') as Mode) || 'minimal';
  });

  // Применяем тему к data-атрибуту
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
  }, [theme]);

  useEffect(() => {
    document.documentElement.setAttribute('data-mode', mode);
    localStorage.setItem('mode', mode);
  }, [mode]);

  const setTheme = (newTheme: Theme) => setThemeState(newTheme);
  const setMode = (newMode: Mode) => {
    // Если подписки нет, можно только переключиться на minimal
    if (newMode === 'premium' && !isPremium) return;
    setModeState(newMode);
  };

  return (
    <ThemeContext.Provider value={{
      theme, mode, setTheme, setMode, isPremium
    }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used within ThemeProvider');
  return context;
}
```

---

## 13. Настройки (Settings Page)

```
frontend/src/auth/components/
└── SettingsPage.tsx

Секции настроек:
1. Тема (Theme)
   - Переключатель: Тёмная / Светлая
2. Режим дизайна (Design Mode)
   - Минималистичный (всегда доступен)
   - Премиум (доступен при isPremium === true)
3. Профиль (Profile)
   - Email
   - Смена пароля
4. Подписка (Subscription)
   - Статус подписки
   - Кнопка "Оформить подписку" (если нет подписки)
```

---

## 14. Чек-лист реализации

- [ ] Подключить Google Fonts (Roboto)
- [ ] Создать CSS-токены для обеих тем
- [ ] Создать CSS-токены для обоих режимов дизайна
- [ ] Реализовать ThemeContext с переключением `data-theme` и `data-mode`
- [ ] Создать утилитарные классы (glow, gradient-text, gradient-border)
- [ ] Стилизовать кнопки (4 варианта × 2 режима = 8 стилей)
- [ ] Стилизовать карточки (2 режима)
- [ ] Стилизовать поля ввода (4 состояния × 2 режима)
- [ ] Стилизовать навигацию (2 режима)
- [ ] Реализовать страницу настроек с переключателями
- [ ] Добавить анимации (hover, transitions)
- [ ] Проверить адаптивность (mobile-first)
- [ ] Добавить иконки (Lucide или Material Icons)

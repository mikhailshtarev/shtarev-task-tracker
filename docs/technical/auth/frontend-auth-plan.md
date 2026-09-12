# План работ: авторизация — Frontend

## 0. Контекст для агента

Прежде чем начать, прочитай в этом порядке:

1. `AGENTS.md` в корне репозитория — общие правила (минимальные изменения, стиль, итог работы).
2. [frontend-auth-integration.md](frontend-auth-integration.md) — основной контракт: сценарии, коды ошибок, хранение токенов, структура компонентов.
3. [authentication.md](authentication.md) — разделы 4 (API-контракты) и 6.6/6.7 — при необходимости свериться с серверным поведением.

**Правила ведения плана:**

- Выполняй этапы строго по порядку, они зависят друг от друга.
- Отмечай выполненные пункты (`- [x]`) в этом файле после каждого завершённого этапа — это твой якорь контекста.
- Не переходи к следующему этапу, пока не выполнены критерии готовности текущего.
- Не изменяй файлы вне `frontend/src/`, `frontend/index.html` и `frontend/package.json`, кроме случаев, явно указанных в плане.
- Все тексты интерфейса — на русском, в соответствии с сообщениями из `frontend-auth-integration.md`.
- Существующие компоненты (`AuthPage.tsx`, стили в `src/styles/`) — используй их стилистику; не переписывай тему/дизайн-систему.

## 1. Инфраструктура

- [x] Добавить зависимости в `frontend/package.json` с обоснованием в PR/коммите:
  - `react-router-dom` — роутинг (`ProtectedRoute`, страницы авторизации); без него план не реализуем;
  - `vitest` + `@testing-library/react` + `jsdom` (devDependencies) — тесты (этап 7); стандартный набор для Vite-проекта, отдельная тестовая инфраструктура не нужна.
- [x] Настроить `vitest` в `vite.config.ts` (`environment: 'jsdom'`), script `"test": "vitest run"` в `package.json`.

**Готовность:** `npm run build` и `npm test` выполняются без ошибок.

## 2. API-слой и токены

- [x] `src/auth/utils/tokenManager.ts` — `setAccessToken` / `getAccessToken` / `clearAccessToken` (только память, раздел 3.1 контракта).
- [x] `src/api/client.ts` — обёртка над `fetch` с `credentials: 'include'`, класс `ApiError` с `status` и `error.code/message/details` (пример в разделе 8 контракта).
- [x] `src/auth/services/authService.ts` — типизированные обёртки над всеми endpoints из раздела 2 контракта: `register`, `confirm`, `resendConfirmation`, `login`, `me`, `refresh`, `logout`, `changePassword`, `google`, `forgotPassword`, `resetPassword`.
- [x] `src/api/refreshInterceptor.ts` — при `401`: один общий `POST /auth/refresh`, остальные запросы ждут его результата (раздел 3.3 и пример в разделе 9 контракта); при неудаче — очистка токена и редирект на `/login`.

**Готовность:** все вызовы API типизированы, `npm run build` проходит.

## 3. Валидация

- [x] `src/auth/utils/validators.ts` — клиентские правила из раздела 2.1 контракта: email (формат, 5–254), пароль (8–128, заглавная + строчная + цифра), сообщения об ошибках на русском.

**Готовность:** юнит-тесты валидатора написаны (этап 7).

## 4. AuthContext и восстановление сессии

- [x] `src/auth/context/AuthContext.tsx` + `src/auth/hooks/useAuth.ts` — состояние по разделу 5.2 контракта: `accessToken`, `user`, `isAuthLoading`, `isAuthenticated`, методы `login/register/logout/loginWithGoogle/confirmEmail/resendConfirmation/forgotPassword/resetPassword/changePassword`.
- [x] Silent refresh при старте приложения (раздел 3.4 контракта): при монтировании `AuthContext` вызвать `POST /auth/refresh`; при 200 — сохранить токен, загрузить `GET /auth/me`; при 401 — считать пользователя гостем. До завершения — `isAuthLoading = true`, без редиректов.

**Готовность:** после перезагрузки страницы с валидным refresh-cookie пользователь остаётся залогинен (ручная проверка + тест этапа 7).

## 5. Компоненты и маршруты

Структура — по разделу 5.1 контракта (`src/auth/components/`). Каждый компонент — одна ответственность, ошибки показывать через `ErrorDisplay` (раздел 4.3 контракта).

- [x] `LoginForm.tsx` — email + пароль, Google-кнопка; обработка 401 (`INVALID_CREDENTIALS`, `EMAIL_NOT_CONFIRMED` + кнопка повторной отправки), 429 (блокировка кнопки).
- [x] `RegisterForm.tsx` — валидация на клиенте; 201 → экран «Подтвердите email»; 409 → ошибка под полем email.
- [x] `EmailConfirmPage.tsx` — извлекает `?token`, вызывает `confirm`; при `INVALID_TOKEN` — ошибка и кнопка «Отправить письмо повторно» (`resendConfirmation`).
- [x] `ForgotPasswordForm.tsx` — 200 → нейтральное сообщение (без раскрытия факта регистрации).
- [x] `ResetPasswordForm.tsx` — извлекает `?token`, новый пароль; 409 `PASSWORD_TOO_RECENT` → под полем.
- [x] `ChangePasswordForm.tsx` — текущий + новый пароль; при 200 — очистка state и редирект на `/login` (все устройства разлогинены).
- [x] `GoogleLoginButton.tsx` — генерирует `state` (криптослучайный), сохраняет в `sessionStorage`, редирект на Google consent URL (раздел 2.9 контракта); `client_id` — из конфига (`import.meta.env.VITE_GOOGLE_CLIENT_ID`).
- [x] `GoogleCallbackPage.tsx` — валидирует `state` против `sessionStorage` (при несовпадении — ошибка без отправки `code`), извлекает `code`, вызывает `loginWithGoogle`.
- [x] `src/routes/ProtectedRoute.tsx` — по разделу 5.3 контракта: при `isAuthLoading` — индикатор загрузки, без редиректа.
- [x] Роутинг по таблице раздела 6 контракта: `/login`, `/register`, `/confirm-email`, `/forgot-password`, `/reset-password`, `/google-callback`, `/change-password`, `/dashboard`, `/*` → NotFound. Формы разместить на существующих страницах/лейауте проекта, переиспользуя текущие стили.

**Готовность:** все маршруты открываются, навигация между экранами соответствует сценариям раздела 2 контракта.

## 6. Интеграционные связки

- [x] После `login` и Google-входа — загрузка `GET /auth/me` и заполнение `user` в контексте.
- [x] `logout` — очистка `accessToken` и `user`, редирект на `/login` (и при 204, и при 401).
- [x] `dashboard` — защищённый маршрут за `ProtectedRoute` (заглушка-страница, если дашборда ещё нет).

**Готовность:** сквозной сценарий «регистрация → подтверждение → вход → перезагрузка страницы (сессия сохранена) → выход» работает вручную через dev-сервер.

## 7. Тестирование

Настроена инфраструктура — Vitest + React Testing Library (этап 1). Написать тест-кейсы, покрывающие следующие сценарии:

**Юнит-тесты:**

| # | Сценарий | Ожидание |
| --- | --- | --- |
| F-01 | `validators`: валидный email / пароль | Ошибок нет |
| F-02 | `validators`: пароль без заглавной / без цифры / короче 8 / длиннее 128 | Понятное сообщение под полем |
| F-03 | `tokenManager`: set → get → clear | Токен доступен и очищается; в `localStorage`/`sessionStorage` ничего не попадает |
| F-04 | `refreshInterceptor`: два параллельных 401 | Выполнен ровно один `POST /auth/refresh`, оба запроса повторены с новым токеном |
| F-05 | `refreshInterceptor`: refresh вернул 401 | Токен очищен, редирект на `/login` |

**Компонентные тесты:**

| # | Сценарий | Ожидание |
| --- | --- | --- |
| F-06 | `LoginForm`: сабмит с невалидными полями | Запрос не отправлен, ошибки валидации под полями |
| F-07 | `LoginForm`: API вернул `INVALID_CREDENTIALS` | Сообщение «Неверный email или пароль» |
| F-08 | `LoginForm`: API вернул `EMAIL_NOT_CONFIRMED` | Сообщение + кнопка повторной отправки письма |
| F-09 | `LoginForm`: API вернул `RATE_LIMITED` | Сообщение, кнопка заблокирована |
| F-10 | `RegisterForm`: сабмит с валидными данными → 201 | Экран «Подтвердите email» |
| F-11 | `RegisterForm`: 409 `EMAIL_ALREADY_EXISTS` | Ошибка под полем email |
| F-12 | `EmailConfirmPage`: `?token=...` → 200 | Редирект на `/login` |
| F-13 | `EmailConfirmPage`: 400 `INVALID_TOKEN` | Ошибка + кнопка повторной отправки |
| F-14 | `GoogleCallbackPage`: `state` из URL не совпадает с `sessionStorage` | Ошибка, запрос `POST /auth/google` не отправлен |
| F-15 | `ResetPasswordForm`: 409 `PASSWORD_TOO_RECENT` | Ошибка под полем нового пароля |
| F-16 | `ChangePasswordForm`: 200 | Очистка state, редирект на `/login` |
| F-17 | `ProtectedRoute`: гость → `/dashboard` | Редирект на `/login`; при `isAuthLoading` редиректа нет |
| F-18 | Silent refresh: мок `POST /auth/refresh` → 200 + `GET /auth/me` → 200 | Пользователь залогинен после «перезагрузки» |

API мокируется на уровне `fetch`/`authService` — тесты не требуют работающего бэкенда.

**Готовность:** `npm test` — все кейсы зелёные; `npm run build` — без ошибок.

# Интеграция фронтенда с API авторизации

## 1. Обзор

Документ описывает контракт API авторизации и инструкции по интеграции фронтенда с сервисом `auth`. Все endpoints находятся в basePath `/api/v1/auth`.

### 1.1. Общие положения

| Параметр | Значение |
| --- | --- |
| Base path | `/api/v1/auth` |
| Формат тела запросов/ответов | JSON (`application/json`) |
| Формат ошибок | `{ "error": { "code": "...", "message": "...", "details": [...] } }` |
| Хранение access token | В памяти (переменная, не localStorage) |
| Хранение refresh token | HTTP-only, Secure cookie (автоматически отправляется браузером) |
| Восстановление сессии | Silent refresh при старте приложения (см. 3.4) |
| CORS | Фронтенд должен отправлять запросы с `credentials: 'include'` |

---

## 2. Сценарии использования

### 2.1. Регистрация

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. Пользователь заполняет форму (email, пароль)                  │
│ 2. Фронтенд валидирует ввод на клиенте                           │
│ 3. POST /api/v1/auth/register                                    │
│ 4. Если 201 → показать экран "Подтвердите email"                 │
│ 5. Если 409 → показать "Email уже зарегистрирован"               │
│ 6. Если 400 → показать ошибки валидации                          │
│ 7. Если 429 → показать "Слишком много запросов"                  │
└──────────────────────────────────────────────────────────────────┘
```

**Request:**
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass1"
}
```

**Responses:**

| HTTP | Тело | Действие фронтенда |
| --- | --- | --- |
| `201 Created` | `{ "message": "Проверьте email для подтверждения" }` | Перенаправить на экран подтверждения email |
| `400 Bad Request` | `{ "error": { "code": "VALIDATION_ERROR", "message": "...", "details": [...] } }` | Показать ошибки валидации в форме |
| `409 Conflict` | `{ "error": { "code": "EMAIL_ALREADY_EXISTS", "message": "Пользователь с таким email уже существует" } }` | Показать ошибку под полем email |
| `429 Too Many Requests` | `{ "error": { "code": "RATE_LIMITED", "message": "..." } }` | Показать сообщение, заблокировать кнопку |

**Клиентская валидация:**

| Поле | Правила |
| --- | --- |
| `email` | Email формат, длина 5–254 символа |
| `password` | 8–128 символов, ≥1 заглавная, ≥1 строчная, ≥1 цифра |

---

### 2.2. Подтверждение email

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. Пользователь переходит по ссылке из email                     │
│ 2. Фронтенд извлекает ?token=... из URL                          │
│ 3. GET /api/v1/auth/confirm?token=...                            │
│ 4. Если 200 → перенаправить на экран входа                       │
│ 5. Если 400 → показать "Неверный или истёкший токен"             │
│              и кнопку "Отправить письмо повторно" (см. 2.3)      │
└──────────────────────────────────────────────────────────────────┘
```

**Request:**
```http
GET /api/v1/auth/confirm?token=eyJ...
```

**Responses:**

| HTTP | Тело | Действие фронтенда |
| --- | --- | --- |
| `200 OK` | `{ "message": "Email подтверждён" }` | Перенаправить на `/login` |
| `400 Bad Request` | `{ "error": { "code": "INVALID_TOKEN", "message": "Неверный или истёкший токен подтверждения" } }` | Показать ошибку и кнопку повторной отправки письма |

---

### 2.3. Повторная отправка письма подтверждения

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. Пользователь нажимает "Отправить письмо повторно"             │
│ 2. POST /api/v1/auth/resend-confirmation                         │
│ 3. Если 200 → показать "Если email зарегистрирован и не          │
│                подтверждён, вы получите письмо"                  │
│ 4. Если 429 → показать "Слишком много запросов"                  │
└──────────────────────────────────────────────────────────────────┘
```

**Request:**
```http
POST /api/v1/auth/resend-confirmation
Content-Type: application/json

{
  "email": "user@example.com"
}
```

**Responses:**

| HTTP | Тело | Действие фронтенда |
| --- | --- | --- |
| `200 OK` | `{ "message": "Если email зарегистрирован и не подтверждён, вы получите письмо" }` | Показать сообщение об успехе |
| `400 Bad Request` | `{ "error": { "code": "VALIDATION_ERROR", ... } }` | Показать ошибку валидации |
| `429 Too Many Requests` | `{ "error": { "code": "RATE_LIMITED", "message": "..." } }` | Показать сообщение, заблокировать кнопку |

---

### 2.4. Вход в систему (Login)

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. Пользователь вводит email и пароль                             │
│ 2. POST /api/v1/auth/login                                        │
│ 3. Если 200 → сохранить accessToken в state, перенаправить на дашборд │
│ 4. Если 401 → показать "Неверный email или пароль"               │
│ 5. Если 429 → показать "Слишком много попыток. Попробуйте через 15 минут" │
└──────────────────────────────────────────────────────────────────┘
```

**Request:**
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass1"
}
```

**Responses:**

| HTTP | Тело / Headers | Действие фронтенда |
| --- | --- | --- |
| `200 OK` | Тело: `{ "accessToken": "eyJ..." }`<br>Cookie: `refreshToken=...; HttpOnly; Secure; SameSite=Strict` | Сохранить accessToken в переменную, загрузить данные пользователя (`GET /auth/me`, см. 2.5), перенаправить на дашборд |
| `400 Bad Request` | `{ "error": { "code": "VALIDATION_ERROR", ... } }` | Показать ошибки валидации |
| `401 Unauthorized` | `{ "error": { "code": "INVALID_CREDENTIALS", "message": "Неверный email или пароль" } }` или `{ "error": { "code": "EMAIL_NOT_CONFIRMED", "message": "Подтвердите email для входа" } }` | Показать ошибку; при `EMAIL_NOT_CONFIRMED` — дополнительно кнопку повторной отправки письма (см. 2.3) |
| `429 Too Many Requests` | `{ "error": { "code": "RATE_LIMITED", "message": "Слишком много попыток. Попробуйте через 15 минут" } }` | Показать сообщение, заблокировать кнопку на 15 минут |

---

### 2.5. Данные пользователя (Me)

После успешного входа, Google-входа или silent refresh фронтенд загружает данные пользователя для `AuthContext`.

**Request:**
```http
GET /api/v1/auth/me
Authorization: Bearer {accessToken}
```

**Responses:**

| HTTP | Тело | Действие фронтенда |
| --- | --- | --- |
| `200 OK` | `{ "id": "UUID", "email": "user@example.com", "name": "Иван" }` | Сохранить `user` в `AuthContext` |
| `401 Unauthorized` | `{ "error": { "code": "UNAUTHORIZED", "message": "Требуется авторизация" } }` | Очистить state, перенаправить на `/login` |

---

### 2.6. Обновление токена (Refresh)

```
┌──────────────────────────────────────────────────────────────────┐
│ Автоматический процесс при истечении access token (15 мин):      │
│ 1. Поймать 401 от защищённого endpoint                           │
│ 2. POST /api/v1/auth/refresh (cookie refreshToken отправляется автоматически) │
│ 3. Если 200 → получить новый accessToken, повторить исходный запрос │
│ 4. Если 401 → очистить state, перенаправить на /login             │
└──────────────────────────────────────────────────────────────────┘
```

**Request:**
```http
POST /api/v1/auth/refresh
Cookie: refreshToken=eyJ...
```

**Responses:**

| HTTP | Тело / Headers | Действие фронтенда |
| --- | --- | --- |
| `200 OK` | Тело: `{ "accessToken": "eyJ..." }`<br>Cookie: `refreshToken=new_value` | Обновить accessToken в state |
| `401 Unauthorized` | `{ "error": { "code": "INVALID_REFRESH_TOKEN", "message": "Токен обновления недействителен" } }` | Очистить accessToken, перенаправить на `/login` |

---

### 2.7. Выход из системы (Logout)

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. Пользователь нажимает "Выйти"                                 │
│ 2. POST /api/v1/auth/logout (cookie автоматически)               │
│ 3. Если 204 → очистить accessToken в state, перенаправить на /login │
└──────────────────────────────────────────────────────────────────┘
```

**Request:**
```http
POST /api/v1/auth/logout
Cookie: refreshToken=eyJ...
```

**Responses:**

| HTTP | Действие фронтенда |
| --- | --- |
| `204 No Content` | Очистить accessToken, перенаправить на `/login` |
| `401 Unauthorized` | Очистить accessToken, перенаправить на `/login` |

---

### 2.8. Смена пароля (Change Password)

Доступно авторизованному пользователю (экран настроек).

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. Пользователь вводит текущий и новый пароль                    │
│ 2. POST /api/v1/auth/change-password (Bearer token)              │
│ 3. Если 200 → показать "Пароль успешно изменён", перенаправить   │
│    на /login (все устройства разлогинены)                        │
│ 4. Если 400 → показать ошибку (неверный текущий пароль или       │
│    валидация)                                                    │
│ 5. Если 409 → показать "Нельзя использовать последний пароль"    │
└──────────────────────────────────────────────────────────────────┘
```

**Request:**
```http
POST /api/v1/auth/change-password
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "currentPassword": "SecurePass1",
  "newPassword": "NewSecurePass2"
}
```

**Responses:**

| HTTP | Тело | Действие фронтенда |
| --- | --- | --- |
| `200 OK` | `{ "message": "Пароль успешно изменён" }` | Очистить state, перенаправить на `/login` |
| `400 Bad Request` | `{ "error": { "code": "INVALID_CURRENT_PASSWORD", "message": "Неверный текущий пароль" } }` или `VALIDATION_ERROR` | Показать ошибку под соответствующим полем |
| `401 Unauthorized` | `{ "error": { "code": "UNAUTHORIZED", ... } }` | Стандартная обработка истёкшего токена (refresh / редирект) |
| `409 Conflict` | `{ "error": { "code": "PASSWORD_TOO_RECENT", "message": "Нельзя использовать последний пароль" } }` | Показать ошибку под полем нового пароля |

**Клиентская валидация:** 8–128 символов, ≥1 заглавная, ≥1 строчная, ≥1 цифра.

---

### 2.9. Вход через Google OAuth 2.0

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. Пользователь нажимает "Войти через Google"                    │
│ 2. Фронтенд генерирует state (случайная строка), сохраняет его   │
│    в sessionStorage и открывает Google consent screen:           │
│    https://accounts.google.com/o/oauth2/v2/auth                  │
│      ?client_id={GOOGLE_CLIENT_ID}  — из конфига фронтенда       │
│      &redirect_uri={origin}/google-callback                      │
│      &response_type=code                                         │
│      &scope=openid email profile                                 │
│      &state={state}                                              │
│ 3. Google перенаправляет на /google-callback?code=...&state=...  │
│ 4. Страница callback проверяет state === sessionStorage.state    │
│    (при несовпадении — показать ошибку, не отправлять code)      │
│ 5. Фронтенд извлекает code и отправляет POST /api/v1/auth/google │
│ 6. Если 200 → сохранить accessToken, загрузить /me,              │
│    перенаправить на дашборд                                      │
│ 7. Если 409 → показать "Email уже зарегистрирован.               │
│    Войдите через email" и перенаправить на /login                │
└──────────────────────────────────────────────────────────────────┘
```

**Request:**
```http
POST /api/v1/auth/google
Content-Type: application/json

{
  "code": "4/0AX4XfWh..."
}
```

**Responses:**

| HTTP | Тело / Headers | Действие фронтенда |
| --- | --- | --- |
| `200 OK` | Тело: `{ "accessToken": "eyJ..." }`<br>Cookie: `refreshToken=...` | Сохранить accessToken, загрузить `/me`, перенаправить на дашборд |
| `400 Bad Request` | `{ "error": { "code": "INVALID_GOOGLE_CODE", "message": "Неверный код авторизации Google" } }` | Показать ошибку, предложить повторный вход |
| `409 Conflict` | `{ "error": { "code": "EMAIL_CONFLICT", "message": "Email уже зарегистрирован. Войдите через email" } }` | Показать ошибку, перенаправить на `/login` |

---

### 2.10. Сброс пароля (Forgot Password)

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. Пользователь вводит email на экране "Забыли пароль?"          │
│ 2. POST /api/v1/auth/forgot-password                             │
│ 3. Если 200 → показать "Если email зарегистрирован, вы получите  │
│                ссылку для сброса пароля"                          │
└──────────────────────────────────────────────────────────────────┘
```

**Request:**
```http
POST /api/v1/auth/forgot-password
Content-Type: application/json

{
  "email": "user@example.com"
}
```

**Responses:**

| HTTP | Тело | Действие фронтенда |
| --- | --- | --- |
| `200 OK` | `{ "message": "Если email зарегистрирован, вы получите ссылку для сброса пароля" }` | Показать сообщение об успехе |
| `400 Bad Request` | `{ "error": { "code": "VALIDATION_ERROR", ... } }` | Показать ошибку валидации |
| `429 Too Many Requests` | `{ "error": { "code": "RATE_LIMITED", "message": "..." } }` | Показать сообщение, заблокировать кнопку |

---

### 2.11. Сброс пароля (Reset Password)

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. Пользователь переходит по ссылке из email с ?token=...        │
│ 2. Вводит новый пароль                                           │
│ 3. POST /api/v1/auth/reset-password с token и новым паролем      │
│ 4. Если 200 → показать "Пароль успешно изменён", перенаправить на /login │
│ 5. Если 400 → показать "Неверный или истёкший токен"             │
│ 6. Если 409 → показать "Нельзя использовать последний пароль"    │
└──────────────────────────────────────────────────────────────────┘
```

**Request:**
```http
POST /api/v1/auth/reset-password
Content-Type: application/json

{
  "token": "eyJ...",
  "password": "NewSecurePass1"
}
```

**Responses:**

| HTTP | Тело | Действие фронтенда |
| --- | --- | --- |
| `200 OK` | `{ "message": "Пароль успешно изменён" }` | Перенаправить на `/login` |
| `400 Bad Request` | `{ "error": { "code": "INVALID_TOKEN", "message": "Неверный или истёкший токен" } }` или `VALIDATION_ERROR` | Показать ошибку |
| `409 Conflict` | `{ "error": { "code": "PASSWORD_TOO_RECENT", "message": "Нельзя использовать последний пароль" } }` | Показать ошибку под полем пароля |

---

## 3. Работа с токенами

### 3.1. Хранение access token

Access token **НЕ** должен храниться в `localStorage`, `sessionStorage` или cookie. Только в памяти:

```typescript
// Пример: контекст или хук для управления токеном
let accessToken: string | null = null;

function setAccessToken(token: string) {
  accessToken = token;
}

function getAccessToken(): string | null {
  return accessToken;
}

function clearAccessToken() {
  accessToken = null;
}
```

### 3.2. Отправка access token

Для защищённых запросов (не `/api/v1/auth/**`) использовать заголовок `Authorization`:

```typescript
const response = await fetch('/api/v1/tasks', {
  method: 'GET',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${accessToken}`,
  },
  credentials: 'include', // для отправки cookie
});
```

### 3.3. Автоматическое обновление токена

Реализовать interceptor (для axios) или обёртку для fetch, которая:

1. Поймает `401` от защищённого endpoint.
2. Попытается обновить токен через `POST /api/v1/auth/refresh`.
3. Если обновление успешно — повторит исходный запрос с новым токеном.
4. Если обновление не удалось — очистит state и перенаправит на `/login`.

**Важно:** При параллельных запросах, которые получили `401`, нужно выполнить только один запрос на refresh, а остальные — дождаться его результата.

### 3.4. Восстановление сессии при загрузке приложения

Access token хранится только в памяти и теряется при перезагрузке страницы. Поэтому при старте приложения фронтенд выполняет **silent refresh**:

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. При монтировании приложения показать индикатор загрузки       │
│    (не рендерить /login и не считать пользователя гостем сразу)  │
│ 2. POST /api/v1/auth/refresh (cookie отправится автоматически)   │
│ 3. Если 200 → сохранить accessToken, загрузить GET /auth/me,     │
│    показать дашборд — пользователь остаётся залогинен            │
│ 4. Если 401 → показать экран входа                               │
└──────────────────────────────────────────────────────────────────┘
```

До завершения silent refresh приложение находится в состоянии `isAuthLoading = true` и не выполняет редиректов — иначе при каждой перезагрузке страницы пользователь будет кратковременно «выбрасываться» на `/login`.

---

## 4. Обработка ошибок

### 4.1. Универсальный формат ответа об ошибке

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Понятное сообщение для пользователя",
    "details": [
      {
        "field": "email",
        "message": "Неверный формат email"
      }
    ]
  }
}
```

### 4.2. Коды ошибок API

| Code | HTTP | Описание | Действие фронтенда |
| --- | --- | --- | --- |
| `VALIDATION_ERROR` | 400 | Ошибка валидации входных данных | Показать `details` под соответствующими полями формы |
| `EMAIL_ALREADY_EXISTS` | 409 | Email уже зарегистрирован | Показать под полем email |
| `INVALID_TOKEN` | 400 | Неверный или истёкший токен | Показать сообщение об ошибке, предложить повторную отправку письма |
| `INVALID_CREDENTIALS` | 401 | Неверный email или пароль | Показать под формой входа |
| `EMAIL_NOT_CONFIRMED` | 401 | Email не подтверждён | Показать сообщение и кнопку повторной отправки письма (см. 2.3) |
| `RATE_LIMITED` | 429 | Превышен лимит попыток | Показать сообщение, заблокировать форму |
| `INVALID_REFRESH_TOKEN` | 401 | Refresh токен недействителен | Очистить state, перенаправить на `/login` |
| `INVALID_GOOGLE_CODE` | 400 | Неверный код Google OAuth | Показать ошибку, предложить повторить |
| `EMAIL_CONFLICT` | 409 | Email занят аккаунтом с паролем | Перенаправить на `/login` |
| `INVALID_CURRENT_PASSWORD` | 400 | Неверный текущий пароль (смена пароля) | Показать под полем текущего пароля |
| `PASSWORD_TOO_RECENT` | 409 | Новый пароль совпадает с недавним | Показать под полем нового пароля |
| `UNAUTHORIZED` | 401 | Bearer токен отсутствует или недействителен | Очистить state, перенаправить на `/login` |
| `ORIGIN_NOT_ALLOWED` | 403 | Запрос с недопустимого Origin | Не обрабатывать (защита CSRF, легитимный фронтенд не получит) |

### 4.3. Пример компонента для отображения ошибок

```typescript
function ErrorDisplay({ error }: { error: ApiError | null }) {
  if (!error) return null;

  return (
    <div className="error-message" role="alert">
      {error.message}
      {error.details?.map((detail, i) => (
        <div key={i} className="error-detail">
          {detail.message}
        </div>
      ))}
    </div>
  );
}
```

---

## 5. Рекомендации по структуре фронтенда

### 5.1. Рекомендуемые компоненты

```
frontend/src/
├── auth/
│   ├── context/
│   │   └── AuthContext.tsx          # Контекст авторизации (accessToken, user)
│   ├── hooks/
│   │   └── useAuth.ts               # Хук для работы с авторизацией
│   ├── components/
│   │   ├── LoginForm.tsx            # Форма входа
│   │   ├── RegisterForm.tsx         # Форма регистрации
│   │   ├── ForgotPasswordForm.tsx   # Форма "Забыли пароль?"
│   │   ├── ResetPasswordForm.tsx    # Форма сброса пароля
│   │   ├── ChangePasswordForm.tsx   # Форма смены пароля (настройки)
│   │   ├── EmailConfirmPage.tsx     # Экран подтверждения email
│   │   ├── GoogleCallbackPage.tsx   # Страница /google-callback (state + code)
│   │   └── GoogleLoginButton.tsx    # Кнопка "Войти через Google"
│   ├── services/
│   │   └── authService.ts           # Обёртки над API вызовами
│   └── utils/
│       ├── validators.ts            # Клиентская валидация
│       └── tokenManager.ts          # Управление токенами
├── routes/
│   └── ProtectedRoute.tsx           # Обёртка для защищённых роутов
└── api/
    └── client.ts                    # Базовый fetch/axios клиент с interceptors
```

### 5.2. AuthContext

```typescript
// Примерная структура
interface AuthContextValue {
  accessToken: string | null;
  user: User | null;                       // заполняется из GET /auth/me
  isAuthLoading: boolean;                  // true до завершения silent refresh
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  loginWithGoogle: (code: string) => Promise<void>;
  confirmEmail: (token: string) => Promise<void>;
  resendConfirmation: (email: string) => Promise<void>;
  forgotPassword: (email: string) => Promise<void>;
  resetPassword: (token: string, password: string) => Promise<void>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  isAuthenticated: boolean;
}
```

### 5.3. ProtectedRoute

```typescript
// Обёртка для защищённых роутов
function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isAuthLoading } = useAuth();

  if (isAuthLoading) {
    return <div className="app-loading" />; // silent refresh ещё выполняется
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
```

---

## 6. Роутинг

| Route | Компонент | Доступ |
| --- | --- | --- |
| `/login` | LoginForm | Гости + авторизованные (перенаправление на дашборд) |
| `/register` | RegisterForm | Гости |
| `/confirm-email` | EmailConfirmPage | Гости (извлекает `?token` из URL) |
| `/forgot-password` | ForgotPasswordForm | Гости |
| `/reset-password` | ResetPasswordForm | Гости (извлекает `?token` из URL) |
| `/google-callback` | GoogleCallbackPage | Все (валидация `state`, обмен `code`) |
| `/change-password` | ChangePasswordForm | Авторизованные |
| `/dashboard` | Dashboard | Авторизованные |
| `/*` | NotFound | Все |

---

## 7. Безопасность

1. **Access token** — только в памяти, никогда в `localStorage`/`sessionStorage`.
2. **Refresh token** — в HTTP-only cookie, управляется сервером.
3. **credentials: 'include'** — обязательно для всех запросов к `/api/v1/auth/**`.
4. **Валидация на сервере** — клиентская валидация не заменяет серверную.
5. **HTTPS** — в production все запросы должны идти по HTTPS (Secure cookie).
6. **XSS защита** — экранировать все пользовательские данные в JSX (React делает это автоматически).
7. **CSRF** — refresh cookie имеет `SameSite=Strict`; сервер дополнительно проверяет заголовок `Origin` на `/auth/refresh` и `/auth/logout`. От фронтенда дополнительных действий не требуется.
8. **Google OAuth** — параметр `state` обязателен: генерируется до редиректа, хранится в `sessionStorage`, проверяется на `/google-callback` до отправки `code` в API.

---

## 8. Пример: обёртка над fetch

```typescript
// api/client.ts
const BASE_URL = '/api/v1';

async function request<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const response = await fetch(`${BASE_URL}${endpoint}`, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new ApiError(response.status, error?.error);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

class ApiError extends Error {
  constructor(
    public status: number,
    public detail: ApiErrorDetail | null
  ) {
    super(detail?.message || 'Ошибка сервера');
  }
}
```

---

## 9. Пример: refresh interceptor

```typescript
// api/refreshInterceptor.ts
let isRefreshing = false;
let pendingRequests: Array<{
  resolve: (token: string) => void;
  reject: (error: Error) => void;
}> = [];

function handleAccessTokenExpired(error: ApiError) {
  if (error.detail?.code !== 'INVALID_REFRESH_TOKEN' && error.status !== 401) {
    throw error;
  }

  if (!isRefreshing) {
    isRefreshing = true;

    return refreshAccessToken()
      .then((token) => {
        pendingRequests.forEach((cb) => cb.resolve(token));
        pendingRequests = [];
      })
      .catch((error) => {
        pendingRequests.forEach((cb) => cb.reject(error));
        pendingRequests = [];
        clearAccessToken();
        window.location.href = '/login';
      })
      .finally(() => {
        isRefreshing = false;
      });
  }

  return new Promise<string>((resolve, reject) => {
    pendingRequests.push({ resolve, reject });
  });
}

async function refreshAccessToken(): Promise<string> {
  const data = await request<{ accessToken: string }>('/auth/refresh', {
    method: 'POST',
  });
  setAccessToken(data.accessToken);
  return data.accessToken;
}
```

---

## 10. Зависимости

Для реализации авторизации потребуются:

| Зависимость | Назначение |
| --- | --- |
| `react-router-dom` | Роутинг (ProtectedRoute, навигация) |
| `axios` (опционально) | HTTP-клиент с interceptors (или нативный `fetch`) |

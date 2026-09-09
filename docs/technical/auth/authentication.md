# Аутентификация и авторизация — техническое задание

## 1. Обзор

Документ описывает схему аутентификации, API-контракты и схему базы данных для сервиса `auth`. Разработан на основе бизнес-требований ([business-requirements.md](../../product/business-requirements.md)) и нефункциональных требований ([non-functional-requirements.md](../../product/non-functional-requirements.md), раздел 2).

### 1.1. Цели

- Регистрация и авторизация пользователя по email + пароль.
- Вход через Google OAuth 2.0.
- Выдача JWT access и refresh токенов.
- Безопасное хранение паролей.
- Защита от brute-force и повторного использования refresh-токенов.
- Подтверждение email и восстановление пароля.
- Смена пароля авторизованным пользователем.

### 1.2. Стек

| Компонент | Технология |
| --- | --- |
| Язык | Java 25 |
| Фреймворк | Spring Boot 4.1.1 |
| JWT | jjwt 0.12.x |
| Хеширование паролей | Argon2 (spring-security-crypto) |
| База данных | PostgreSQL |
| Миграции | Flyway |

Google OAuth 2.0 реализуется вручную через `GoogleOAuth2Service` (обмен authorization code на ID token через Google Token Endpoint). Spring Security OAuth 2.0 Client не используется — он рассчитан на серверный redirect-флоу и не подходит для SPA-сценария, описанного в разделе 2.2.

---

## 2. Схема аутентификации

### 2.1. Flow: Email + Password

```
┌──────────┐                          ┌──────────┐
│ Frontend │                          │  Auth    │
│          │── POST /auth/register ──▶│          │
│          ││  {email, password}       ││          │
│          ││                          ││          │
│          │◀── 201 Created ──────────││          │
│          ││  { message: "..." }      ││          │
│          ││                          ││          │
│          │── POST /auth/login ──────▶││          │
│          ││  {email, password}       ││          │
│          ││                          ││          │
│          │◀── 200 OK ───────────────││          │
│          ││  { accessToken }         ││          │
│          ││  cookie: refreshToken    ││          │
│          ││                          ││          │
│          │── GET /auth/refresh ─────▶││          │
│          ││  (cookie: refreshToken)  ││          │
│          ││                          ││          │
│          │◀── 200 OK ───────────────││          │
│          ││  { accessToken }         ││          │
│          ││  cookie: new refreshToken││          │
│          ││                          ││          │
│          │── POST /auth/logout ─────▶││          │
│          ││  (cookie: refreshToken)  ││          │
│          ││                          ││          │
│          │◀── 204 No Content ───────││          │
└──────────┘                          └──────────┘
```

### 2.2. Flow: Google OAuth 2.0

Фронтенд формирует ссылку на Google consent screen самостоятельно:

```
https://accounts.google.com/o/oauth2/v2/auth
    ?client_id={GOOGLE_CLIENT_ID}        — из конфигурации фронтенда
    &redirect_uri={origin}/google-callback
    &response_type=code
    &scope=openid email profile
    &state={случайная строка}            — защита OAuth-флоу от CSRF
```

```
┌──────────┐                 ┌──────────┐        ┌────────┐
│ Frontend │                 │  Auth    │        │ Google │
│          │── Redirect ──────────────────────────────▶│
│          │                 │          │        │        │
│          │◀── Redirect back ──────────────────────── │
│          ││  ?code=...&state=...     ││        │        │
│          ││                          ││        │        │
│          │  (валидация state против ││        │        │
│          │   sessionStorage)        ││        │        │
│          │── POST /auth/google ────▶││        │        │
│          ││  { code }                ││── обмен code    │
│          ││                          ││   на ID token   │
│          │◀── 200 OK ───────────────││        │        │
│          ││  { accessToken }         ││        │        │
│          ││  cookie: refreshToken    ││        │        │
└──────────┘                 └──────────┘        └────────┘
```

`state` генерируется фронтендом перед редиректом, сохраняется в `sessionStorage` и проверяется на странице `/google-callback` до отправки `code` в API.

### 2.3. Flow: Подтверждение email

```
┌──────────┐                          ┌──────────┐
│ Frontend │                          │  Auth    │
│          │── POST /auth/register ──▶│          │
│          ││  {email, password}       ││          │
│          ││                          ││          │
│          │◀── 201 Created ──────────││          │
│          ││  { message: "Подтвердите││          │
│          ││   email" }               ││          │
│          ││                          ││          │
│          │  (User receives email    ││          │
│          │   with confirmation link) ││          │
│          ││                          ││          │
│          │── GET /auth/confirm ─────▶││          │
│          ││  ?token=...              ││          │
│          ││                          ││          │
│          │◀── 200 OK ───────────────││          │
│          ││  { message: "Email      ││          │
│          ││   подтверждён" }         ││          │
│          ││                          ││          │
│          │── POST /auth/login ──────▶││          │
│          ││  {email, password}       ││          │
│          ││                          ││          │
│          │◀── 200 OK ───────────────││          │
│          ││  { accessToken }         ││          │
│          ││  cookie: refreshToken    ││          │
└──────────┘                          └──────────┘
```

Если пользователь не получил письмо, он может запросить повторную отправку через `POST /auth/resend-confirmation`.

### 2.4. Параметры токенов

| Параметр | Значение |
| --- | --- |
| Формат токена | JWT (JSON Web Token) |
| Алгоритм подписи | RS256 |
| Заголовок `kid` | Да — идентификатор ключа для JWKS-ротации |
| Access token TTL | 15 минут |
| Refresh token TTL | 7 дней |
| Хранение refresh token | HTTP-only, Secure cookie |
| Хранение access token | В памяти frontend |
| Single-use refresh | Да — каждый refresh токен используется один раз |

### 2.5. Параметры безопасности

| Параметр | Значение |
| --- | --- |
| Алгоритм хеширования пароля | Argon2id |
| timeCost | 2 |
| memoryCost | 65536 |
| parallelism | 1 |
| Минимальная длина пароля | 8 символов |
| Максимальная длина пароля | 128 символов |
| Требования к сложности | Минимум одна заглавная, одна строчная буква, одна цифра |
| Блокировка входа по пользователю | 5 неудачных попыток → 15 минут |
| Блокировка входа по IP | 20 неудачных попыток → 15 минут |
| Rate limit register / forgot-password | 5 запросов по IP за час |
| Rate limit resend-confirmation | 3 запроса по IP за час |
| История последних паролей | 5 |

---

## 3. Схема базы данных

### 3.1. ER-диаграмма

```
┌──────────────────┐       ┌──────────────────────────┐
│     users        │       │    password_history       │
├──────────────────┤       ├──────────────────────────┤
│ id          UUID │◀──┐   │ id             UUID      │
│ email      TEXT  │  │   │ user_id          UUID ─────┼──▶ users.id
│ password   TEXT  │  │   │ password_hash    TEXT      │
│ name       TEXT  │  │   │ created_at   TIMESTAMP     │
│ is_confirmed BOOLEAN│ │  └──────────────────────────┘
│ tokens_valid_from│  │
│ created_at TIMESTAMP│ │  ┌──────────────────────────┐
│ updated_at TIMESTAMP│ │  │ refresh_token_blacklist   │
│ last_login_at    │  └──▶├──────────────────────────┤
└──────────────────┘      │ id             UUID       │
                          │ jti            TEXT       │
                          │ user_id          UUID ─────┼──▶ users.id
                          │ expires_at TIMESTAMP      │
                          └──────────────────────────┘

┌──────────────────────────────┐  ┌──────────────────────────────┐
│  email_confirmation_tokens   │  │    password_reset_tokens     │
├──────────────────────────────┤  ├──────────────────────────────┤
│ id             UUID          │  │ id             UUID          │
│ token_hash     TEXT          │  │ token_hash     TEXT          │
│ user_id          UUID ───────────▶ users.id                  │
│ expires_at     TIMESTAMP     │  │ expires_at     TIMESTAMP     │
│ created_at     TIMESTAMP     │  │ created_at     TIMESTAMP     │
└──────────────────────────────┘  └──────────────────────────────┘

┌──────────────────────────┐
│    login_attempts         │
├──────────────────────────┤
│ id             UUID       │
│ user_id          UUID ────────▶ users.id (NULL для неизвестного email)
│ ip_address     INET       │
│ endpoint       TEXT       │
│ failed_at      TIMESTAMP  │
└──────────────────────────┘
```

### 3.2. Таблица `users`

| Столбец | Тип | Ограничения | Описание |
| --- | --- | --- | --- |
| `id` | `UUID` | `PRIMARY KEY`, `DEFAULT gen_random_uuid()` | Уникальный идентификатор |
| `email` | `TEXT` | `UNIQUE`, `NOT NULL`, `LOWER(email)` | Email пользователя (уникальный, в нижнем регистре) |
| `password` | `TEXT` | `NULL` | Хеш пароля (NULL для Google-пользователей) |
| `name` | `TEXT` | `NULL` | Имя пользователя (из Google ID token; для email-регистрации — NULL) |
| `is_confirmed` | `BOOLEAN` | `NOT NULL`, `DEFAULT false` | Подтверждён ли email |
| `tokens_valid_from` | `TIMESTAMP` | `NULL` | Все refresh токены с `iat` раньше этого момента отклоняются (принудительный logout всех устройств при смене/сбросе пароля) |
| `created_at` | `TIMESTAMP` | `NOT NULL`, `DEFAULT now()` | Дата создания |
| `updated_at` | `TIMESTAMP` | `NOT NULL`, `DEFAULT now()` | Дата последнего обновления |
| `last_login_at` | `TIMESTAMP` | `NULL` | Время последнего входа |

**Индексы:**

| Имя | Столбцы | Тип |
| --- | --- | --- |
| `idx_users_email` | `email` | UNIQUE |

### 3.3. Таблица `password_history`

| Столбец | Тип | Ограничения | Описание |
| --- | --- | --- | --- |
| `id` | `UUID` | `PRIMARY KEY`, `DEFAULT gen_random_uuid()` | Уникальный идентификатор |
| `user_id` | `UUID` | `NOT NULL`, `REFERENCES users(id) ON DELETE CASCADE` | Ссылка на пользователя |
| `password_hash` | `TEXT` | `NOT NULL` | Хеш использованного пароля |
| `created_at` | `TIMESTAMP` | `NOT NULL`, `DEFAULT now()` | Дата использования пароля |

**Индексы:**

| Имя | Столбцы | Тип |
| --- | --- | --- |
| `idx_password_history_user` | `user_id` | — |

**Очистка:** для пользователя достаточно последних 5 записей; более старые удаляются при добавлении нового хеша.

### 3.4. Таблица `refresh_token_blacklist`

Хранит `jti` (JWT ID) использованных refresh токенов для single-use механизма.

| Столбец | Тип | Ограничения | Описание |
| --- | --- | --- | --- |
| `id` | `UUID` | `PRIMARY KEY`, `DEFAULT gen_random_uuid()` | Уникальный идентификатор |
| `jti` | `TEXT` | `NOT NULL` | JWT ID из claim'а токена |
| `user_id` | `UUID` | `NOT NULL`, `REFERENCES users(id) ON DELETE CASCADE` | Ссылка на пользователя |
| `expires_at` | `TIMESTAMP` | `NOT NULL` | Срок действия токена (из JWT `exp`) |

**Индексы:**

| Имя | Столбцы | Тип |
| --- | --- | --- |
| `idx_refresh_blacklist_jti` | `jti` | UNIQUE |
| `idx_refresh_blacklist_user` | `user_id` | — |

**Single-use атомарность:** новая пара токенов выдаётся только после успешного `INSERT` старого `jti` в blacklist. UNIQUE-ограничение на `jti` закрывает гонку при параллельных запросах: второй `INSERT` с тем же `jti` падает, и запрос получает `401`. Первая версия рассчитана на один инстанс приложения; при горизонтальном масштабировании механизм сохраняется без изменений.

**Очистка:** Записи, у которых `expires_at < now()`, удаляются фоновой задачей (cron, каждые 6 часов).

### 3.5. Таблица `email_confirmation_tokens`

Токены подтверждения email. В БД хранится SHA-256 хеш токена — сырой токен существует только в ссылке из письма.

| Столбец | Тип | Ограничения | Описание |
| --- | --- | --- | --- |
| `id` | `UUID` | `PRIMARY KEY`, `DEFAULT gen_random_uuid()` | Уникальный идентификатор |
| `token_hash` | `TEXT` | `NOT NULL`, `UNIQUE` | SHA-256 хеш токена подтверждения |
| `user_id` | `UUID` | `NOT NULL`, `REFERENCES users(id) ON DELETE CASCADE` | Ссылка на пользователя |
| `expires_at` | `TIMESTAMP` | `NOT NULL` | Срок действия (24 часа с момента создания) |
| `created_at` | `TIMESTAMP` | `NOT NULL`, `DEFAULT now()` | Дата создания |

**Индексы:**

| Имя | Столбцы | Тип |
| --- | --- | --- |
| `idx_email_confirmation_token` | `token_hash` | UNIQUE |
| `idx_email_confirmation_user` | `user_id` | — |

**Очистка:** истёкшие и использованные токены удаляются при подтверждении и фоновой задачей (cron, каждые 6 часов).

### 3.6. Таблица `password_reset_tokens`

Токены сброса пароля. В БД хранится SHA-256 хеш токена — сырой токен существует только в ссылке из письма.

| Столбец | Тип | Ограничения | Описание |
| --- | --- | --- | --- |
| `id` | `UUID` | `PRIMARY KEY`, `DEFAULT gen_random_uuid()` | Уникальный идентификатор |
| `token_hash` | `TEXT` | `NOT NULL`, `UNIQUE` | SHA-256 хеш токена сброса |
| `user_id` | `UUID` | `NOT NULL`, `REFERENCES users(id) ON DELETE CASCADE` | Ссылка на пользователя |
| `expires_at` | `TIMESTAMP` | `NOT NULL` | Срок действия (1 час с момента создания) |
| `created_at` | `TIMESTAMP` | `NOT NULL`, `DEFAULT now()` | Дата создания |

**Индексы:**

| Имя | Столбцы | Тип |
| --- | --- | --- |
| `idx_password_reset_token` | `token_hash` | UNIQUE |
| `idx_password_reset_user` | `user_id` | — |

**Очистка:** истёкшие и использованные токены удаляются при сбросе пароля и фоновой задачей (cron, каждые 6 часов).

### 3.7. Таблица `login_attempts`

Неудачные попытки входа и запросы на регистрацию/письма для rate limiting. `user_id` может быть `NULL` — так фиксируются попытки подбора пароля к несуществующему email (блокировка по IP).

| Столбец | Тип | Ограничения | Описание |
| --- | --- | --- | --- |
| `id` | `UUID` | `PRIMARY KEY`, `DEFAULT gen_random_uuid()` | Уникальный идентификатор |
| `user_id` | `UUID` | `NULL`, `REFERENCES users(id) ON DELETE CASCADE` | Ссылка на пользователя (NULL, если пользователь не найден) |
| `ip_address` | `INET` | `NOT NULL` | IP-адрес попытки |
| `endpoint` | `TEXT` | `NOT NULL` | Endpoint (`login`, `register`, `forgot-password`, `resend-confirmation`) |
| `failed_at` | `TIMESTAMP` | `NOT NULL`, `DEFAULT now()` | Время неудачной попытки |

**Индексы:**

| Имя | Столбцы | Тип |
| --- | --- | --- |
| `idx_login_attempts_user` | `user_id` | — |
| `idx_login_attempts_ip` | `ip_address` | — |
| `idx_login_attempts_endpoint_ip` | `endpoint, ip_address` | — |

**Очистка:** Записи старше 24 часов удаляются фоновой задачей.

### 3.8. SQL-миграция (Flyway)

Файл: `V1__init_auth_schema.sql`

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email            TEXT         NOT NULL UNIQUE,
    password         TEXT,
    name             TEXT,
    is_confirmed     BOOLEAN      NOT NULL DEFAULT false,
    tokens_valid_from TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    last_login_at    TIMESTAMP
);

CREATE TABLE password_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash TEXT         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token_blacklist (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jti        TEXT         NOT NULL UNIQUE,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP    NOT NULL
);

CREATE TABLE email_confirmation_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash TEXT         NOT NULL UNIQUE,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash TEXT         NOT NULL UNIQUE,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE login_attempts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         REFERENCES users(id) ON DELETE CASCADE,
    ip_address INET         NOT NULL,
    endpoint   TEXT         NOT NULL,
    failed_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_history_user ON password_history (user_id);
CREATE INDEX idx_refresh_blacklist_jti ON refresh_token_blacklist (jti);
CREATE INDEX idx_refresh_blacklist_user ON refresh_token_blacklist (user_id);
CREATE INDEX idx_email_confirmation_token ON email_confirmation_tokens (token_hash);
CREATE INDEX idx_email_confirmation_user ON email_confirmation_tokens (user_id);
CREATE INDEX idx_password_reset_token ON password_reset_tokens (token_hash);
CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);
CREATE INDEX idx_login_attempts_user ON login_attempts (user_id);
CREATE INDEX idx_login_attempts_ip ON login_attempts (ip_address);
CREATE INDEX idx_login_attempts_endpoint_ip ON login_attempts (endpoint, ip_address);
```

---

## 4. API-контракты

Все endpoints находятся в basePath `/api/v1/auth`.

### 4.1. Формат ошибок

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Понятное сообщение для пользователя",
    "details": null
  }
}
```

### 4.2. POST `/api/v1/auth/register`

Регистрация нового пользователя.

**Request body:**

```json
{
  "email": "user@example.com",
  "password": "SecurePass1"
}
```

| Поле | Тип | Обязательность | Валидация |
| --- | --- | --- | --- |
| `email` | string | Да | Email формат, 5–254 символа |
| `password` | string | Да | 8–128 символов, заглавная + строчная буква + цифра |

**Responses:**

| HTTP код | Условие | Тело |
| --- | --- | --- |
| `201 Created` | Успешная регистрация | `{"message": "Проверьте email для подтверждения"}` |
| `400 Bad Request` | Ошибка валидации | `{ "error": { "code": "VALIDATION_ERROR", "message": "...", "details": [...] } }` |
| `409 Conflict` | Email уже зарегистрирован | `{ "error": { "code": "EMAIL_ALREADY_EXISTS", "message": "Пользователь с таким email уже существует" } }` |
| `429 Too Many Requests` | Превышен лимит по IP (5 за час) | `{ "error": { "code": "RATE_LIMITED", "message": "Слишком много запросов. Попробуйте позже" } }` |

**Поведение:**

1. Проверяет IP rate limit.
2. Валидирует email и пароль.
3. Проверяет уникальность email.
4. Хеширует пароль через Argon2id.
5. Создаёт запись в `users` с `is_confirmed = false`.
6. Сохраняет хеш в `password_history`.
7. Генерирует токен подтверждения, сохраняет его SHA-256 хеш в `email_confirmation_tokens` (TTL 24 часа) и отправляет email (заглушка — логирование).
8. Возвращает `201`.

---

### 4.3. GET `/api/v1/auth/confirm`

Подтверждение email.

**Query parameters:**

| Параметр | Тип | Обязательность | Описание |
| --- | --- | --- | --- |
| `token` | string | Да | Токен подтверждения из email |

**Responses:**

| HTTP код | Условие | Тело |
| --- | --- | --- |
| `200 OK` | Успешное подтверждение | `{"message": "Email подтверждён"}` |
| `400 Bad Request` | Неверный или истёкший токен | `{ "error": { "code": "INVALID_TOKEN", "message": "Неверный или истёкший токен подтверждения" } }` |

**Поведение:**

1. Хеширует токен и ищет его в `email_confirmation_tokens`.
2. Валидирует срок действия (24 часа).
3. Устанавливает `is_confirmed = true` для пользователя.
4. Удаляет токен подтверждения.

---

### 4.4. POST `/api/v1/auth/resend-confirmation`

Повторная отправка письма подтверждения email.

**Request body:**

```json
{
  "email": "user@example.com"
}
```

| Поле | Тип | Обязательность | Валидация |
| --- | --- | --- | --- |
| `email` | string | Да | Email формат, 5–254 символа |

**Responses:**

| HTTP код | Условие | Тело |
| --- | --- | --- |
| `200 OK` | Запрос обработан | `{"message": "Если email зарегистрирован и не подтверждён, вы получите письмо"}` |
| `400 Bad Request` | Неверный формат email | Стандартный формат ошибок |
| `429 Too Many Requests` | Превышен лимит по IP (3 за час) | `{ "error": { "code": "RATE_LIMITED", "message": "Слишком много запросов. Попробуйте позже" } }` |

**Поведение:**

1. Проверяет IP rate limit.
2. Ищет пользователя по email.
3. Если пользователь найден, email не подтверждён — генерирует новый токен (TTL 24 часа), сохраняет хеш в `email_confirmation_tokens`, отправляет email (заглушка — логирование).
4. Если пользователь не найден или email уже подтверждён — всё равно возвращает `200` (без раскрытия статуса регистрации).

---

### 4.5. POST `/api/v1/auth/login`

Вход в систему.

**Request body:**

```json
{
  "email": "user@example.com",
  "password": "SecurePass1"
}
```

**Responses:**

| HTTP код | Условие | Тело / Headers |
| --- | --- | --- |
| `200 OK` | Успешный вход | Тело: `{ "accessToken": "eyJ..." }`<br>Cookie: `refreshToken=...; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=604800` |
| `400 Bad Request` | Неверный формат | Стандартный формат ошибок |
| `401 Unauthorized` | Неверный email/пароль или email не подтверждён | `{ "error": { "code": "INVALID_CREDENTIALS", "message": "Неверный email или пароль" } }` или `{ "error": { "code": "EMAIL_NOT_CONFIRMED", "message": "Подтвердите email для входа" } }` |
| `429 Too Many Requests` | Превышен лимит попыток | `{ "error": { "code": "RATE_LIMITED", "message": "Слишком много попыток. Попробуйте через 15 минут" } }` |

**Поведение:**

1. Проверяет IP rate limit: ≥ 20 неудачных попыток `login` с этого IP за последние 15 минут → `RATE_LIMITED`.
2. Ищет пользователя по email (LOWER).
3. Если пользователь не найден — записывает попытку в `login_attempts` (`user_id = NULL`) и возвращает `INVALID_CREDENTIALS` (без раскрытия факта отсутствия пользователя).
4. Проверяет пользовательский rate limit: ≥ 5 неудачных попыток пользователя за последние 15 минут → `RATE_LIMITED`.
5. Проверяет `is_confirmed`. Если `false` — возвращает `EMAIL_NOT_CONFIRMED`.
6. Проверяет пароль через Argon2.
7. При неудаче — записывает попытку в `login_attempts`.
8. При успехе — генерирует access + refresh токены, обновляет `last_login_at`, очищает неудачные попытки пользователя.
9. Возвращает access token в теле, refresh — в HTTP-only cookie.

---

### 4.6. GET `/api/v1/auth/me`

Данные текущего пользователя. Требует Bearer access token.

**Responses:**

| HTTP код | Условие | Тело |
| --- | --- | --- |
| `200 OK` | Успешный запрос | `{ "id": "UUID", "email": "user@example.com", "name": "Иван" }` |
| `401 Unauthorized` | Токен отсутствует, недействителен или истёк | `{ "error": { "code": "UNAUTHORIZED", "message": "Требуется авторизация" } }` |

**Поведение:**

1. Извлекает `user.id` из access token.
2. Возвращает `id`, `email` и `name` пользователя.

---

### 4.7. POST `/api/v1/auth/refresh`

Обновление access токена.

**Cookies:**

| Имя | Описание |
| --- | --- |
| `refreshToken` | Refresh token в HTTP-only cookie |

**Responses:**

| HTTP код | Условие | Тело / Headers |
| --- | --- | --- |
| `200 OK` | Успешное обновление | Тело: `{ "accessToken": "eyJ..." }`<br>Cookie: `refreshToken=new_value; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=604800` |
| `401 Unauthorized` | Refresh токен недействителен или истёк | `{ "error": { "code": "INVALID_REFRESH_TOKEN", "message": "Токен обновления недействителен" } }` |

**Поведение:**

1. Извлекает refresh token из cookie.
2. Валидирует JWT (подпись, срок действия).
3. Проверяет, что `iat` токена не раньше `users.tokens_valid_from` (иначе токен отозван сменой/сбросом пароля).
4. Извлекает `jti` из токена, проверяет, что его нет в `refresh_token_blacklist`.
5. Если `jti` в blacklist — возвращает `401` (токен уже использован).
6. Генерирует новый access token и новый refresh token.
7. Добавляет старый `jti` в `refresh_token_blacklist` с `expires_at = exp` из старого токена. Новые токены выдаются только после успешного `INSERT` (атомарность через UNIQUE на `jti`).

---

### 4.8. POST `/api/v1/auth/logout`

Выход из системы.

**Cookies:**

| Имя | Описание |
| --- | --- |
| `refreshToken` | Refresh token |

**Responses:**

| HTTP код | Условие |
| --- | --- |
| `204 No Content` | Успешный выход |
| `401 Unauthorized` | Refresh токен недействителен |

**Поведение:**

1. Извлекает refresh token из cookie.
2. Извлекает `jti`, добавляет в `refresh_token_blacklist`.
3. Удаляет cookie (`Max-Age=0`).

---

### 4.9. POST `/api/v1/auth/change-password`

Смена пароля авторизованным пользователем. Требует Bearer access token.

**Request body:**

```json
{
  "currentPassword": "SecurePass1",
  "newPassword": "NewSecurePass2"
}
```

| Поле | Тип | Обязательность | Валидация |
| --- | --- | --- | --- |
| `currentPassword` | string | Да | Текущий пароль |
| `newPassword` | string | Да | 8–128 символов, заглавная + строчная буква + цифра |

**Responses:**

| HTTP код | Условие | Тело |
| --- | --- | --- |
| `200 OK` | Пароль изменён | `{"message": "Пароль успешно изменён"}` |
| `400 Bad Request` | Неверный текущий пароль | `{ "error": { "code": "INVALID_CURRENT_PASSWORD", "message": "Неверный текущий пароль" } }` |
| `400 Bad Request` | Ошибка валидации нового пароля | Стандартный формат ошибок |
| `401 Unauthorized` | Access token отсутствует/недействителен | `{ "error": { "code": "UNAUTHORIZED", "message": "Требуется авторизация" } }` |
| `409 Conflict` | Новый пароль совпадает с одним из последних 5 | `{ "error": { "code": "PASSWORD_TOO_RECENT", "message": "Нельзя использовать последний пароль" } }` |

**Поведение:**

1. Извлекает `user.id` из access token.
2. Проверяет `currentPassword` через Argon2.
3. Валидирует `newPassword`, проверяет, что он не совпадает с последними 5 паролями из `password_history`.
4. Хеширует `newPassword`, обновляет `users.password`, добавляет хеш в `password_history` (оставляет последние 5 записей).
5. Устанавливает `users.tokens_valid_from = now()` — все refresh токены на всех устройствах становятся недействительными (принудительный logout).
6. Возвращает `200`.

---

### 4.10. POST `/api/v1/auth/google`

Вход/регистрация через Google OAuth 2.0.

**Request body:**

```json
{
  "code": "4/0AX4XfWh..."
}
```

| Поле | Тип | Обязательность | Описание |
| --- | --- | --- | --- |
| `code` | string | Да | OAuth 2.0 authorization code от Google |

**Responses:**

| HTTP код | Условие | Тело / Headers |
| --- | --- | --- |
| `200 OK` | Успешный вход/регистрация | Тело: `{ "accessToken": "eyJ..." }`<br>Cookie: `refreshToken=...` |
| `400 Bad Request` | Неверный code | `{ "error": { "code": "INVALID_GOOGLE_CODE", "message": "Неверный код авторизации Google" } }` |
| `409 Conflict` | Email уже занят другим аккаунтом | `{ "error": { "code": "EMAIL_CONFLICT", "message": "Email уже зарегистрирован. Войдите через email" } }` |

**Поведение:**

1. Обменивает `code` на Google ID token через Google Token Endpoint (`client_secret` — из конфигурации окружения).
2. Валидирует ID token (проверяет signature, issuer, audience).
3. Извлекает `email`, `name`, `picture` из ID token.
4. Ищет пользователя по email.
5. Если не найден — создаёт нового пользователя с `password = NULL`, `name` из ID token, `is_confirmed = true`.
6. Если найден и `password IS NULL` — авторизует.
7. Если найден и `password IS NOT NULL` — возвращает `EMAIL_CONFLICT`.
8. Генерирует access + refresh токены.

---

### 4.11. POST `/api/v1/auth/forgot-password`

Запрос на сброс пароля.

**Request body:**

```json
{
  "email": "user@example.com"
}
```

**Responses:**

| HTTP код | Условие | Тело |
| --- | --- | --- |
| `200 OK` | Запрос обработан | `{"message": "Если email зарегистрирован, вы получите ссылку для сброса пароля"}` |
| `400 Bad Request` | Неверный формат email | Стандартный формат ошибок |
| `429 Too Many Requests` | Превышен лимит по IP (5 за час) | `{ "error": { "code": "RATE_LIMITED", "message": "Слишком много запросов. Попробуйте позже" } }` |

**Поведение:**

1. Проверяет IP rate limit.
2. Ищет пользователя по email.
3. Если найден — генерирует токен сброса (TTL 1 час), сохраняет его SHA-256 хеш в `password_reset_tokens`, отправляет email (заглушка — логирование).
4. Если не найден — всё равно возвращает `200` (без раскрытия факта регистрации).

---

### 4.12. POST `/api/v1/auth/reset-password`

Сброс пароля.

**Request body:**

```json
{
  "token": "eyJ...",
  "password": "NewSecurePass1"
}
```

| Поле | Тип | Обязательность | Валидация |
| --- | --- | --- | --- |
| `token` | string | Да | Токен из email |
| `password` | string | Да | 8–128 символов, заглавная + строчная буква + цифра |

**Responses:**

| HTTP код | Условие | Тело |
| --- | --- | --- |
| `200 OK` | Пароль сброшен | `{"message": "Пароль успешно изменён"}` |
| `400 Bad Request` | Неверный или истёкший токен | `{ "error": { "code": "INVALID_TOKEN", "message": "Неверный или истёкший токен" } }` |
| `400 Bad Request` | Ошибка валидации пароля | Стандартный формат ошибок |
| `409 Conflict` | Новый пароль совпадает с одним из последних 5 | `{ "error": { "code": "PASSWORD_TOO_RECENT", "message": "Нельзя использовать последний пароль" } }` |

**Поведение:**

1. Хеширует токен и валидирует его в `password_reset_tokens` (срок действия — 1 час).
2. Проверяет, что новый пароль не совпадает с последними 5 паролями из `password_history`.
3. Хеширует новый пароль и обновляет `users.password`, добавляет хеш в `password_history`.
4. Удаляет все токены сброса для пользователя.
5. Устанавливает `users.tokens_valid_from = now()` — все refresh токены пользователя становятся недействительными (принудительный выход на всех устройствах).

---

### 4.13. GET `/api/v1/auth/.well-known/jwks.json`

Публичный JWKS-эндпоинт для валидации access токенов другими сервисами (Gateway, Tasks, Notes и т. д.). См. раздел 6.7.

**Responses:**

| HTTP код | Условие | Тело |
| --- | --- | --- |
| `200 OK` | — | Стандартный JWKS-документ: `{ "keys": [ { "kty": "RSA", "use": "sig", "alg": "RS256", "kid": "...", "n": "...", "e": "AQAB" } ] }` |

---

## 5. Структура проекта

```
backend/auth/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── ru/
        │       └── shatrev/
        │           └── auth/
        │               ├── AuthApplication.java
        │               ├── config/
        │               │   ├── SecurityConfig.java
        │               │   ├── JwtConfig.java
        │               │   └── CorsConfig.java
        │               ├── controller/
        │               │   └── AuthController.java
        │               ├── dto/
        │               │   ├── request/
        │               │   │   ├── RegisterRequest.java
        │               │   │   ├── LoginRequest.java
        │               │   │   ├── GoogleRequest.java
        │               │   │   ├── ForgotPasswordRequest.java
        │               │   │   ├── ResetPasswordRequest.java
        │               │   │   ├── ResendConfirmationRequest.java
        │               │   │   └── ChangePasswordRequest.java
        │               │   └── response/
        │               │       ├── AuthResponse.java
        │               │       └── MeResponse.java
        │               ├── entity/
        │               │   ├── User.java
        │               │   ├── PasswordHistory.java
        │               │   ├── LoginAttempt.java
        │               │   ├── RefreshTokenBlacklist.java
        │               │   ├── EmailConfirmationToken.java
        │               │   └── PasswordResetToken.java
        │               ├── repository/
        │               │   ├── UserRepository.java
        │               │   ├── PasswordHistoryRepository.java
        │               │   ├── LoginAttemptRepository.java
        │               │   ├── RefreshTokenBlacklistRepository.java
        │               │   ├── EmailConfirmationTokenRepository.java
        │               │   └── PasswordResetTokenRepository.java
        │               ├── service/
        │               │   ├── AuthService.java
        │               │   ├── JwtService.java
        │               │   ├── EmailService.java (заглушка)
        │               │   └── GoogleOAuth2Service.java
        │               └── security/
        │                   ├── JwtAuthenticationFilter.java
        │                   └── LoginAttemptService.java
        └── resources/
            ├── application.yml
            └── db/
                └── migration/
                    └── V1__init_auth_schema.sql
```

---

## 6. Детали реализации

### 6.1. JWT Service

| Метод | Описание |
| --- | --- |
| `generateAccessToken(User user)` | Создаёт JWT с claim'ами: `sub=user.id`, `email`, `jti=UUID`, `iat`, `exp` (15 мин); заголовок `kid` |
| `generateRefreshToken(User user)` | Создаёт JWT с claim'ами: `sub=user.id`, `type=refresh`, `jti=UUID`, `iat`, `exp` (7 дней); заголовок `kid` |
| `validateToken(String token)` | Проверяет подпись, срок действия |
| `getUserFromToken(String token)` | Извлекает `user.id` из валидного токена |
| `getJtiFromToken(String token)` | Извлекает `jti` для blacklist |
| `getIatFromToken(String token)` | Извлекает `iat` для проверки `tokens_valid_from` |

Ролевой модели в первой версии нет (см. NFR 2.4) — токен содержит только `sub` и `email`.

### 6.2. Login Attempt Service

| Метод | Описание |
| --- | --- |
| `recordAttempt(UUID userId, String ipAddress, String endpoint)` | Записывает неудачную попытку; `userId` может быть `NULL` (неизвестный email) |
| `isBlockedByUser(UUID userId)` | ≥ 5 неудачных `login` пользователя за последние 15 минут |
| `isBlockedByIp(String ipAddress)` | ≥ 20 неудачных `login` с IP за последние 15 минут |
| `isRateLimitedByIp(String ipAddress, String endpoint)` | `register` / `forgot-password`: ≥ 5 за час; `resend-confirmation`: ≥ 3 за час |
| `cleanup()` | Удаляет записи старше 24 часов (cron, каждые 6 часов) |

### 6.3. Refresh Token Blacklist Service

| Метод | Описание |
| --- | --- |
| `addToBlacklist(String jti, UUID userId, Instant expiresAt)` | Добавляет `jti` в blacklist; новые токены выдаются только после успешного `INSERT` |
| `isBlacklisted(String jti)` | Проверяет, есть ли `jti` в blacklist |
| `cleanup()` | Удаляет записи, у которых `expires_at < now()` (cron, каждые 6 часов) |

### 6.4. Email Service (заглушка)

В первой версии — логирование вместо реальной отправки.

| Метод | Описание |
| --- | --- |
| `sendConfirmationEmail(String email, String token)` | Лог: `Confirmation link for {email}: /confirm-email?token={token}` |
| `sendResetPasswordEmail(String email, String token)` | Лог: `Reset password link for {email}: /reset-password?token={token}` |

### 6.5. Spring Security

```
HTTP Request
    │
    ▼
JwtAuthenticationFilter (валидирует Bearer token там, где требуется)
    │
    ▼
DispatcherServlet
    ├── /api/v1/auth/register → публичный
    ├── /api/v1/auth/login → публичный
    ├── /api/v1/auth/confirm → публичный
    ├── /api/v1/auth/resend-confirmation → публичный
    ├── /api/v1/auth/google → публичный
    ├── /api/v1/auth/forgot-password → публичный
    ├── /api/v1/auth/reset-password → публичный
    ├── /api/v1/auth/refresh → публичный (проверяет cookie)
    ├── /api/v1/auth/logout → публичный (проверяет cookie)
    ├── /api/v1/auth/me → требует Bearer token
    ├── /api/v1/auth/change-password → требует Bearer token
    ├── /api/v1/auth/.well-known/jwks.json → публичный
    └── /api/v1/** (остальные сервисы) → требует Bearer token
```

### 6.6. CSRF защита

Принятое решение (первая версия, один инстанс):

1. Все state-changing запросы к другим сервисам используют Bearer token — CSRF не применим (cookies не отправляются).
2. Cookie-based эндпоинты (`/refresh`, `/logout`): refresh cookie имеет `SameSite=Strict`, что блокирует отправку cookie в кросс-сайтовых запросах.
3. Дополнительно на `/refresh` и `/logout` сервер проверяет заголовок `Origin`: если заголовок присутствует и его origin отсутствует в whitelist (`CorsConfig`) — `403` с кодом `ORIGIN_NOT_ALLOWED`.

Double-submit cookie pattern не используется — при `SameSite=Strict` и проверке `Origin` он избыточен. Требование к фронтенду: всегда отправлять `Origin` (браузер делает это автоматически для POST).

### 6.7. Валидация токенов другими сервисами

Access token подписан RS256; остальные сервисы валидируют подпись по публичному ключу:

| Параметр | Значение |
| --- | --- |
| Публикация ключей | `GET /api/v1/auth/.well-known/jwks.json` (JWKS) |
| Идентификация ключа | Заголовок `kid` в каждом JWT |
| Кэширование | Сервисы кэшируют JWKS; при неизвестном `kid` — повторная загрузка |
| Хранение ключей | Пара RSA в конфигурации окружения (env / secret-хранилище). Ключи не попадают в Git |
| Ротация | Новый ключ с новым `kid` добавляется в JWKS рядом со старым; старый удаляется после истечения всех выданных с ним токенов (≥ 7 дней) |

Другие сервисы проверяют: подпись, `exp`, `iss`. Проверку blacklist и `tokens_valid_from` выполняет только сервис `auth` (refresh-флоу) — access token остаётся валидным до истечения (15 минут).

---

## 7. Валидация

### 7.1. Email

- Регулярное выражение: `^[^@]+@[^@]+\.[^@]+$`
- Длина: 5–254 символа
- Хранится в нижнем регистре (`LOWER()`)

### 7.2. Пароль

- Минимум 8 символов, максимум 128 символов
- Минимум одна заглавная буква: `[A-Z]`
- Минимум одна строчная буква: `[a-z]`
- Минимум одна цифра: `[0-9]`
- Регулярное выражение: `^(?=.*[A-Z])(?=.*[a-z])(?=.*\d).{8,128}$`

---

## 8. Риски и ограничения первой версии

| Риск | Описание | Митигация |
| --- | --- | --- |
| Email не отправляется | Заглушка вместо реальной отправки | Реализовать при подключении SMTP |
| Google OAuth | Не настроен клиент | Создать OAuth 2.0 credentials в Google Console: `client_id` (в конфиг фронтенда), `client_secret` (в конфиг окружения), redirect URI `{origin}/google-callback` |
| Блокировка по IP | Rate limiting считается в БД одного инстанса | Достаточно для одного инстанса; распределённый rate limiting — задел на Gateway |
| Single-use refresh | Первая версия рассчитана на один инстанс; атомарность через UNIQUE на `jti` | При горизонтальном масштабировании механизм сохраняется; при необходимости — Redis-кэш blacklist |
| RS256 ключи | Необходимо сгенерировать пару RSA ключей и разместить в конфигурации окружения | JWKS-эндпоинт + `kid`-ротация (раздел 6.7) |

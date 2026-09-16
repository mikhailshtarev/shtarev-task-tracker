# Сервис auth

Сервис аутентификации и авторизации: регистрация по email + пароль, вход через Google OAuth 2.0,
JWT access/refresh токены (RS256, single-use refresh), подтверждение email, восстановление и смена пароля.
Спецификация: `docs/technical/auth/authentication.md`.

## Запуск

Секреты передаются только через переменные окружения (в Git не попадают):

| Переменная | Описание |
| --- | --- |
| `AUTH_DB_URL` | JDBC URL PostgreSQL, например `jdbc:postgresql://localhost:5432/auth` |
| `AUTH_DB_USER`, `AUTH_DB_PASSWORD` | Учётная запись БД |
| `AUTH_REDIS_URL` | URL Redis для кэша настроек (по умолчанию `redis://localhost:6379`) |
| `AUTH_JWT_KID` | Идентификатор текущего ключа подписи (`kid`) |
| `AUTH_JWT_PRIVATE_KEY` | Текущий приватный RSA-ключ (PEM, PKCS#8) |
| `AUTH_JWT_PUBLIC_KEY` | Текущий публичный RSA-ключ (PEM, X.509) |
| `AUTH_JWT_PREVIOUS_KID`, `AUTH_JWT_PREVIOUS_PUBLIC_KEY` | Предыдущий ключ для ротации (опционально) |
| `AUTH_CORS_ALLOWED_ORIGINS` | Whitelist origins через запятую (CORS и проверка `Origin` на `/refresh`, `/logout`) |
| `AUTH_GOOGLE_CLIENT_ID`, `AUTH_GOOGLE_CLIENT_SECRET`, `AUTH_GOOGLE_REDIRECT_URI` | OAuth 2.0 credentials Google |
| `AUTH_PORT` | Порт (по умолчанию 8081) |

### Генерация RSA-ключей (dev)

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out auth-private.pem
openssl pkey -in auth-private.pem -pubout -out auth-public.pem
```

PEM с переносами строк удобно передавать в контейнер через `_FILE`-переменные или secret-хранилище.
При локальном запуске из shell: `export AUTH_JWT_PRIVATE_KEY="$(cat auth-private.pem)"`.

```bash
mvn spring-boot:run
```

Профиль по умолчанию — `local`; прод-настройки задаются окружением. Миграция `V1__init_auth_schema.sql`
применяется автоматически при старте (Flyway).

## Smoke-сценарий (без реальной почты)

Email-заглушка логирует ссылки:

1. `POST /api/v1/auth/register {email, password}` → 201; в логе — `Confirmation link for ...: /confirm-email?token=...`
2. `GET /api/v1/auth/confirm?token=...` → 200
3. `POST /api/v1/auth/login` → 200 `{accessToken}` + cookie `refreshToken`
4. `POST /api/v1/auth/refresh` (с cookie) → 200 новая пара токенов
5. `POST /api/v1/auth/logout` (с cookie) → 204, cookie очищена

## Тесты

- Юнит-тесты: `mvn test` (B-01…B-07 плана backend-auth).
- Интеграционные тесты (B-08…B-28) используют Testcontainers PostgreSQL — нужен Docker и запущенный daemon:
  `mvn test -Pintegration-tests`. Тесты помечены `@Tag("integration")` и по умолчанию исключены из прогона;
  дополнительно они автоматически пропустятся, если Docker недоступен (`@EnabledIf` + `disabledWithoutDocker`).

## Архитектура

- **JWT**: RS256, заголовок `kid`, TTL access 15 мин / refresh 7 дней; JWKS на `GET /api/v1/auth/.well-known/jwks.json`.
- **Single-use refresh**: `jti` использованного токена попадает в `refresh_token_blacklist` (INSERT-first, UNIQUE).
- **Rate limiting**: `login_attempts` — 5/15 мин по пользователю, 20/15 мин по IP, 5/час register и forgot-password,
  3/час resend-confirmation. Cron-очистки каждые 6 часов.
- **Пароли**: Argon2id (timeCost 2, memoryCost 65536, parallelism 1), история последних 5.
- **Cookie refreshToken**: `HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=604800`;
  CSRF-защита `/refresh` и `/logout` — проверка заголовка `Origin` против whitelist.
- Gateway и другие сервисы валидируют access-токены по JWKS (см. `docs/technical/auth/authentication.md`, раздел 6.7).

# План работ: авторизация — Backend (сервис auth)

## 0. Контекст для агента

Прежде чем начать, прочитай в этом порядке:

1. `AGENTS.md` в корне репозитория — общие правила (минимальные изменения, миграции, валидация на сервере).
2. [authentication.md](authentication.md) — основной документ: схема БД и миграция (раздел 3), API-контракты (раздел 4), детали реализации (раздел 6), риски (раздел 8).
3. `../../product/non-functional-requirements.md` — разделы 2 (безопасность) и 15.2 (формат ошибок), если нужна сверка с требованиями.

**Правила ведения плана:**

- Выполняй этапы строго по порядку.
- Отмечай выполненные пункты (`- [x]`) в этом файле — это твой якорь контекста.
- Весь код — в `backend/auth/`, ничего вне этой папки не изменять.
- Схему БД менять только через миграцию Flyway; миграцию `V1` не редактировать после применения — исправления новой миграцией `V2`.
- Каждый эндпоинт валидирует входные данные на сервере (раздел 7 спецификации) и возвращает ошибки в едином формате (раздел 4.1).
- Имена, структуры и сигнатуры — по разделу 5 спецификации (`Структура проекта`).

## 1. Каркас проекта

- [x] `backend/auth/pom.xml` — Java 25, Spring Boot 4.1.1, зависимости: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-security-crypto` (Argon2), `jjwt` 0.12.x, `spring-boot-starter-data-jpa`, `postgresql`, `flyway-core` + `flyway-database-postgresql`, `spring-boot-starter-validation`, `spring-boot-starter-test` + `spring-security-test` (тесты, этап 7), `spring-boot-testcontainers` + `testcontainers-postgresql` (интеграционные тесты).
- [x] `AuthApplication.java`, `application.yml` (профили `local`/`prod`; секреты — только из окружения: RSA-ключи, `client_secret` Google, параметры БД).
- [x] `src/main/resources/db/migration/V1__init_auth_schema.sql` — скопировать SQL из раздела 3.8 спецификации (6 таблиц + индексы), не меняя.

**Готовность:** приложение стартует, Flyway применяет `V1` на чистой БД.

## 2. Сущности и репозитории

- [x] Entity-классы по разделу 5: `User`, `PasswordHistory`, `RefreshTokenBlacklist`, `EmailConfirmationToken`, `PasswordResetToken`, `LoginAttempt`.
- [x] Репозитории Spring Data по тому же разделу; методы для запросов: пользователь по email (LOWER), последние 5 хешей `password_history`, неудачные `login_attempts` за окно (по user и по IP+endpoint), токены по `token_hash`, `jti` в blacklist.

**Готовность:** контекст поднимается, схема маппится без ошибок (проверка на этапе 7 Testcontainers).

## 3. JWT и безопасность

- [x] `config/JwtConfig.java` — загрузка RSA-ключей из окружения, `kid` из конфига; поддержка двух ключей одновременно (ротация, раздел 6.7).
- [x] `service/JwtService.java` — методы по разделу 6.1: `generateAccessToken` / `generateRefreshToken` (claims `sub`, `email`, `type`, `jti`, `iat`, `exp`; заголовок `kid`), `validateToken`, `getUserFromToken`, `getJtiFromToken`, `getIatFromToken`.
- [x] `GET /api/v1/auth/.well-known/jwks.json` — публикация JWKS (раздел 4.13).
- [x] `config/SecurityConfig.java` — правила доступа по схеме раздела 6.5: публичные/cookie/Bearer-эндпоинты; stateless-сессии.
- [x] `security/JwtAuthenticationFilter.java` — валидация Bearer для `/api/v1/auth/me`, `/api/v1/auth/change-password` и остальных защищённых маршрутов `auth`; остальные сервисы не разбирают пользовательский JWT.
- [x] `config/CorsConfig.java` — whitelist origins из окружения.
- [x] CSRF (раздел 6.6): отключён для Bearer-запросов; для `/refresh` и `/logout` — проверка заголовка `Origin` против whitelist → `403 ORIGIN_NOT_ALLOWED`.

**Готовность:** сгенерированный вручную токен проходит `validateToken`; JWKS отдаётся; чужой Origin на `/refresh` получает 403 (проверяется тестом этапа 7).

## 4. Сервисы бизнес-логики

- [x] `security/LoginAttemptService.java` — по разделу 6.2: `recordAttempt` (user_id может быть NULL), `isBlockedByUser` (5/15 мин), `isBlockedByIp` (20/15 мин), `isRateLimitedByIp` (register, forgot-password — 5/час; resend-confirmation — 3/час), `cleanup` (cron каждые 6 часов).
- [x] `service/EmailService.java` — заглушка-логирование по разделу 6.4 (ссылки `/confirm-email?token=...`, `/reset-password?token=...`).
- [x] `service/GoogleOAuth2Service.java` — обмен `code` на ID token через Google Token Endpoint, валидация signature/issuer/audience, извлечение `email`/`name`/`picture`.
- [x] `service/RefreshTokenBlacklistService.java` — `addToBlacklist` (INSERT-first: новые токены выдаются только после успешного INSERT, UNIQUE на `jti`), `isBlacklisted`, `cleanup` (cron каждые 6 часов).
- [x] Хеширование паролей — Argon2id с параметрами раздела 2.5; токены подтверждения/сброса хранить как SHA-256 хеш.

**Готовность:** сервисы покрываются юнит-тестами этапа 7.

## 5. Эндпоинты

`controller/AuthController.java` + DTO из раздела 5 спецификации. Порядок реализации — по зависимостям:

- [x] `POST /auth/register` — поведение раздела 4.2 (rate limit → валидация → уникальность → Argon2 → `password_history` → токен в `email_confirmation_tokens` → письмо-заглушка → 201).
- [x] `GET /auth/confirm` — раздел 4.3 (хеш токена → TTL 24 ч → `is_confirmed = true` → удаление токена).
- [x] `POST /auth/resend-confirmation` — раздел 4.4 (всегда 200 без раскрытия статуса).
- [x] `POST /auth/login` — раздел 4.5 (IP-лимит → поиск по LOWER → user-лимит → `is_confirmed` → Argon2 → `login_attempts` при неудаче → очистка попыток при успехе → токены + cookie).
- [x] `GET /auth/me` — раздел 4.6 (id, email, name).
- [x] `POST /auth/refresh` — раздел 4.7 (валидация JWT → `iat` против `users.tokens_valid_from` → `jti` против blacklist → INSERT-first → новая пара).
- [x] `POST /auth/logout` — раздел 4.8 (jti в blacklist, cookie `Max-Age=0`).
- [x] `POST /auth/change-password` — раздел 4.9 (текущий пароль → история 5 → `tokens_valid_from = now()`).
- [x] `POST /auth/google` — раздел 4.10 (создание пользователя с `password = NULL`, `EMAIL_CONFLICT` при занятом email).
- [x] `POST /auth/forgot-password` — раздел 4.11 (всегда 200).
- [x] `POST /auth/reset-password` — раздел 4.12 (хеш токена → история паролей → смена → удаление токенов → `tokens_valid_from = now()`).

Cookie refreshToken везде: `HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=604800`.

**Готовность:** все 11 эндпоинтов отвечают контрактами раздела 4 (коды, тела, cookie); ошибки — в формате 4.1.

## 6. Финальная сборка

- [x] Cron-очистки: `refresh_token_blacklist` (expires_at < now), `login_attempts` (старше 24 ч), истёкшие/использованные email- и reset-токены.
- [x] Сквозная сверка с `frontend-auth-integration.md`: коды ошибок и тела ответов совпадают с таблицей 4.2 фронтенд-контракта.
- [x] README-заметка в `backend/auth/` о запуске: переменные окружения, генерация RSA-ключей, профиль БД.

**Готовность:** `mvn test` зелёный, приложение стартует и проходит smoke-сценарий «register → confirm (из лога) → login → refresh → logout».

## 7. Тестирование

Инфраструктура: юнит-тесты (JUnit 5 + Mockito) и интеграционные тесты (Testcontainers PostgreSQL, реальные миграции Flyway). Написать тест-кейсы, покрывающие следующие сценарии:

**Юнит-тесты:**

| # | Сценарий | Ожидание |
| --- | --- | --- |
| B-01 | `JwtService`: генерация и валидация access/refresh | Подпись, `kid`, claims (`sub`, `jti`, `type`), TTL 15 мин / 7 дней |
| B-02 | `JwtService`: чужой ключ / истёкший / изменённый токен | `validateToken` отклоняет |
| B-03 | Валидация пароля: без заглавной / без цифры / < 8 / > 128 | Отклонение с понятным сообщением |
| B-04 | Argon2id: хеш и проверка, параметры 2/65536/1 | Проверка проходит, хеш не обратим |
| B-05 | SHA-256 хеширование токенов подтверждения/сброса | В БД не оказывается сырого токена |
| B-06 | `LoginAttemptService`: границы 5/15 мин (user), 20/15 мин (IP), 5/час, 3/час | Блокировка ровно на границе, сброс окна по времени |
| B-07 | `RefreshTokenBlacklistService`: повторный INSERT того же `jti` | Нарушение UNIQUE — гонка закрыта |

**Интеграционные тесты (реальная БД в Testcontainers):**

| # | Сценарий | Ожидание |
| --- | --- | --- |
| B-08 | Flyway `V1` на чистой БД | 6 таблиц и индексы созданы |
| B-09 | Регистрация: валидные данные → 201; повторный email → 409 `EMAIL_ALREADY_EXISTS`; невалидный пароль → 400 `VALIDATION_ERROR` | Как в разделе 4.2 |
| B-10 | Подтверждение email: валидный токен → 200; повторно → 400; токен старше 24 ч → 400 `INVALID_TOKEN` | Как в разделе 4.3 |
| B-11 | Resend-confirmation: несуществующий email → 200 (без раскрытия); 4-й запрос за час → 429 | Как в разделе 4.4 |
| B-12 | Логин: успех → 200 + accessToken + HttpOnly cookie с флагами; `last_login_at` обновлён | Как в разделе 4.5 |
| B-13 | Логин: неверный пароль → 401 `INVALID_CREDENTIALS`; несуществующий email → тот же 401 (ответы идентичны) | Без раскрытия существования пользователя |
| B-14 | Логин неподтверждённым email → 401 `EMAIL_NOT_CONFIRMED` | Как в разделе 4.5 |
| B-15 | Блокировка по пользователю: 5 неверных паролей → 6-я попытка 429 `RATE_LIMITED` | Даже с верным паролем |
| B-16 | Блокировка по IP: 20 неудачных login с одного IP (в т. ч. на несуществующие email, `user_id = NULL`) → 429 | Как в разделе 4.5 |
| B-17 | Refresh: валидный refresh → 200, новый access + новый refresh- cookie; старый refresh повторно → 401 (jti в blacklist) | Single-use |
| B-18 | Refresh: два параллельных запроса с одним refresh-токеном | Ровно один 200, второй 401 (UNIQUE на `jti`) |
| B-19 | Refresh: токен с `iat` раньше `tokens_valid_from` → 401 | Отзыв после смены пароля |
| B-20 | Logout: jti в blacklist, cookie очищена (`Max-Age=0`); повторный refresh этим токеном → 401 | Как в разделе 4.8 |
| B-21 | Change-password: неверный текущий → 400 `INVALID_CURRENT_PASSWORD`; совпадает с одним из последних 5 → 409 `PASSWORD_TOO_RECENT`; успех → 200, старые refresh отклонены (B-19) | Как в разделе 4.9 |
| B-22 | Change-password/me через Gateway без Bearer / с неверным Bearer → 401 `UNAUTHORIZED` | Защищённые маршруты `auth` требуют действительный access JWT, который проверяет сам `auth`; reset-password публичен и использует одноразовый токен |
| B-23 | Google: замоканный обмен code → новый пользователь (`password = NULL`, `is_confirmed = true`); повторный вход → авторизация; email занят пароль-аккаунтом → 409 `EMAIL_CONFLICT` | Как в разделе 4.10 |
| B-24 | Forgot-password: несуществующий email → 200 (ответ неотличим от успеха); найденный → токен в `password_reset_tokens` с TTL 1 ч | Без раскрытия регистрации |
| B-25 | Reset-password: валидный токен → 200, вход новым паролем работает, старым — нет; повторное использование токена → 400; `tokens_valid_from` установлен | Как в разделе 4.12 |
| B-26 | CSRF/Origin: `/auth/refresh` с `Origin` вне whitelist → 403 `ORIGIN_NOT_ALLOWED`; из whitelist → 200 | Как в разделе 6.6 |
| B-27 | `GET /.well-known/jwks.json` → валидный JWKS, `kid` совпадает с подписью выданных токенов | Как в разделе 4.13 |
| B-28 | Формат ошибок всех эндпоинтов: `{ "error": { "code", "message", "details" } }` | Единый формат 4.1 |

**Готовность:** `mvn test` — все кейсы зелёные; кейсы B-08–B-28 выполняются против Testcontainers PostgreSQL.

**Статус реализации (2026-09-10):** этапы 1–6 выполнены; юнит-тесты B-01–B-07 (31 тест) — зелёные (`mvn test`); smoke-сценарий «register → confirm (из лога) → login → me → refresh (single-use) → Origin-проверки → logout → change-password → forgot/reset-password» пройден вручную против локального PostgreSQL 18. Интеграционные тесты B-08–B-28 написаны (`@Tag("integration")`, запуск `mvn test -Pintegration-tests`); для их выполнения нужен Docker — в текущей среде он недоступен, поэтому кейсы B-08–B-28 ещё не прогонялись.

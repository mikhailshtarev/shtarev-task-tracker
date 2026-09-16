CREATE TABLE user_settings (
    user_id           UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    estimation_unit   TEXT    NOT NULL DEFAULT 'hours'
                      CHECK (estimation_unit IN ('hours', 'pomodoros')),
    pomodoro_minutes  INT     NOT NULL DEFAULT 25
                      CHECK (pomodoro_minutes BETWEEN 5 AND 120 AND pomodoro_minutes % 5 = 0),
    game_mode_enabled BOOLEAN NOT NULL DEFAULT false,
    game_base_exp     INT     NOT NULL DEFAULT 5
                      CHECK (game_base_exp BETWEEN 1 AND 100),
    budget_hour_cost  INT     NOT NULL DEFAULT 5
                      CHECK (budget_hour_cost BETWEEN 1 AND 1000),
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now()
);

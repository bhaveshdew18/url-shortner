CREATE TABLE urls (
                      id          BIGSERIAL PRIMARY KEY,
                      short_code  VARCHAR(32) UNIQUE,
                      long_url    TEXT        NOT NULL,
                      created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                      expires_at  TIMESTAMPTZ,
                      click_count BIGINT      NOT NULL DEFAULT 0
);
CREATE TABLE clicks (
                        id            BIGSERIAL PRIMARY KEY,
                        url_id        BIGINT      NOT NULL REFERENCES urls (id) ON DELETE CASCADE,
                        clicked_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                        referrer_host VARCHAR(255),
                        user_agent    VARCHAR(512)
);

CREATE INDEX idx_clicks_url_id_clicked_at ON clicks (url_id, clicked_at);
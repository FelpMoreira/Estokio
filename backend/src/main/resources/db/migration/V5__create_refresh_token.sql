-- Refresh tokens persistidos e revogaveis (Decisao de kickoff #11). Nunca guarda o token
-- em texto puro, so o hash (SHA-256, calculado na aplicacao).
CREATE TABLE refresh_token (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   UUID NOT NULL REFERENCES usuario (id),
    token_hash   VARCHAR(255) NOT NULL,
    expira_em    TIMESTAMPTZ NOT NULL,
    revogado_em  TIMESTAMPTZ,
    criado_em    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_token_usuario_id ON refresh_token (usuario_id);
CREATE UNIQUE INDEX idx_refresh_token_hash ON refresh_token (token_hash);

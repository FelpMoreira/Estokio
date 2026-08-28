-- Planos da plataforma: limites contratados por cada tenant.
CREATE TABLE plano (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome             VARCHAR(100) NOT NULL,
    max_produtos     INTEGER NOT NULL CHECK (max_produtos > 0),
    max_pedidos_mes  INTEGER NOT NULL CHECK (max_pedidos_mes > 0),
    max_usuarios     INTEGER NOT NULL CHECK (max_usuarios > 0),
    preco_centavos   INTEGER NOT NULL CHECK (preco_centavos >= 0),
    ativo            BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now()
);

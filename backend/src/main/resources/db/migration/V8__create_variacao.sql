-- Variacao / SKU: unidade que realmente tem preco e estoque.
CREATE TABLE variacao (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenant (id),
    produto_id        UUID NOT NULL REFERENCES produto (id),
    sku               VARCHAR(60) NOT NULL,
    atributos         JSONB NOT NULL DEFAULT '{}'::jsonb,
    preco_centavos    INTEGER NOT NULL CHECK (preco_centavos >= 0),
    ponto_reposicao   INTEGER NOT NULL DEFAULT 0 CHECK (ponto_reposicao >= 0),
    ativo             BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (tenant_id, sku)
);

CREATE INDEX idx_variacao_tenant_id ON variacao (tenant_id);
CREATE INDEX idx_variacao_produto_id ON variacao (produto_id);

-- Entidade comercial. Nao tem estoque proprio -- isso vive na variacao (SKU).
CREATE TABLE produto (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenant (id),
    categoria_id  UUID REFERENCES categoria (id),
    nome          VARCHAR(200) NOT NULL,
    descricao     TEXT,
    ativo         BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_produto_tenant_id ON produto (tenant_id);
CREATE INDEX idx_produto_categoria_id ON produto (categoria_id);

-- Catalogo (por tenant): categoria de produtos.
CREATE TABLE categoria (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant (id),
    nome      VARCHAR(150) NOT NULL,
    ativo     BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_categoria_tenant_id ON categoria (tenant_id);

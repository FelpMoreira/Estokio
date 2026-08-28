-- Tenant = loja. Unidade de isolamento multi-tenant (ver Multi-Tenancy e RLS no vault).
CREATE TABLE tenant (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome      VARCHAR(150) NOT NULL,
    slug      VARCHAR(150) NOT NULL UNIQUE,
    status    VARCHAR(20) NOT NULL CHECK (status IN ('PENDENTE', 'ATIVA', 'SUSPENSA', 'CANCELADA')),
    plano_id  UUID NOT NULL REFERENCES plano (id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_plano_id ON tenant (plano_id);

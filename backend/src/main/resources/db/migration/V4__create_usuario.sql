-- Usuarios da plataforma. tenant_id e NULL para SUPER_ADMIN e CLIENTE (papeis globais).
-- Nao tem RLS (ver Multi-Tenancy e RLS): login precisa localizar o usuario pelo email
-- antes de saber o tenant.
CREATE TABLE usuario (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID REFERENCES tenant (id),
    nome       VARCHAR(150) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(100) NOT NULL,
    papel      VARCHAR(20) NOT NULL CHECK (papel IN ('SUPER_ADMIN', 'ADMIN_LOJA', 'OPERADOR', 'CLIENTE')),
    ativo      BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_usuario_tenant_por_papel CHECK (
        (papel IN ('SUPER_ADMIN', 'CLIENTE') AND tenant_id IS NULL)
        OR (papel IN ('ADMIN_LOJA', 'OPERADOR') AND tenant_id IS NOT NULL)
    )
);

CREATE INDEX idx_usuario_tenant_id ON usuario (tenant_id);

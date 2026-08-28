-- Alertas automaticos operacionais (job da Fase 2). Deduplicado por (tenant, tipo, chave)
-- enquanto ainda nao resolvido; o alerta se auto-resolve quando a condicao deixa de existir.
CREATE TABLE alerta (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenant (id),
    tipo           VARCHAR(30) NOT NULL CHECK (
        tipo IN ('ESTOQUE_BAIXO', 'ESTOQUE_ZERADO', 'PRODUTO_PARADO', 'PEDIDO_TRAVADO', 'DIVERGENCIA_SALDO', 'LIMITE_PLANO')
    ),
    severidade     VARCHAR(10) NOT NULL CHECK (severidade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')),
    chave          VARCHAR(150) NOT NULL,
    titulo         VARCHAR(200) NOT NULL,
    dados          JSONB NOT NULL DEFAULT '{}'::jsonb,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolvido_em   TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_alerta_dedup_ativo
    ON alerta (tenant_id, tipo, chave)
    WHERE resolvido_em IS NULL;

CREATE INDEX idx_alerta_tenant_id ON alerta (tenant_id);

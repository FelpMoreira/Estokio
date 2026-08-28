-- Ledger append-only de estoque. Fonte da verdade (regra #1 do dominio).
-- idempotency_key NAO fica aqui (Decisao de kickoff #9): um pedido multi-item gera N
-- movimentos de reserva na mesma transacao, e um UNIQUE aqui quebraria a 2a insercao.
-- A chave de idempotencia do fluxo de criacao de pedido vive em pedido.idempotency_key.
CREATE TABLE estoque_movimento (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              UUID NOT NULL REFERENCES tenant (id),
    variacao_id            UUID NOT NULL REFERENCES variacao (id),
    tipo                   VARCHAR(20) NOT NULL CHECK (
        tipo IN ('ENTRADA', 'SAIDA', 'RESERVA', 'LIBERACAO', 'BAIXA', 'DEVOLUCAO', 'AJUSTE', 'INVENTARIO')
    ),
    delta_fisico           INTEGER NOT NULL DEFAULT 0,
    delta_reservado        INTEGER NOT NULL DEFAULT 0,
    motivo                 TEXT,
    origem_tipo            VARCHAR(30),
    origem_id              UUID,
    estorna_movimento_id   BIGINT REFERENCES estoque_movimento (id),
    usuario_id             UUID REFERENCES usuario (id),
    criado_em              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_estoque_movimento_tenant_variacao_criado
    ON estoque_movimento (tenant_id, variacao_id, criado_em DESC);

-- Pedido do cliente final. idempotency_key mora aqui (Decisao de kickoff #9), nao no
-- movimento de estoque: Idempotency-Key do POST /pedidos evita duplo clique/retry gerando
-- dois pedidos, independente de quantos itens (e movimentos) o pedido tenha.
CREATE TABLE pedido (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenant (id),
    cliente_id          UUID NOT NULL REFERENCES usuario (id),
    numero              INTEGER NOT NULL,
    status              VARCHAR(20) NOT NULL CHECK (
        status IN ('PENDENTE', 'CONFIRMADO', 'SEPARANDO', 'ENVIADO', 'ENTREGUE', 'CANCELADO', 'EXPIRADO', 'DEVOLVIDO')
    ),
    subtotal_centavos   INTEGER NOT NULL CHECK (subtotal_centavos >= 0),
    frete_centavos      INTEGER NOT NULL DEFAULT 0 CHECK (frete_centavos >= 0),
    total_centavos      INTEGER NOT NULL CHECK (total_centavos >= 0),
    forma_pagamento     VARCHAR(30) NOT NULL,
    endereco            JSONB NOT NULL,
    reserva_expira_em   TIMESTAMPTZ,
    idempotency_key     VARCHAR(100),
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, numero),
    UNIQUE (idempotency_key)
);

CREATE INDEX idx_pedido_tenant_status_criado ON pedido (tenant_id, status, criado_em DESC);
CREATE INDEX idx_pedido_cliente_id ON pedido (cliente_id);

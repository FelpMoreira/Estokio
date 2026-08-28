-- Historico de transicoes de status do pedido: nenhum status muda sem registro de
-- quem, quando e por que (motivo obrigatorio em CANCELADO/DEVOLVIDO, validado na service).
-- tenant_id adicionado aqui pelo mesmo motivo de pedido_item (Decisao de kickoff, RLS direta).
CREATE TABLE pedido_status_historico (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenant (id),
    pedido_id     UUID NOT NULL REFERENCES pedido (id),
    status_de     VARCHAR(20),
    status_para   VARCHAR(20) NOT NULL,
    usuario_id    UUID REFERENCES usuario (id),
    motivo        TEXT,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pedido_status_historico_pedido_id ON pedido_status_historico (pedido_id);
CREATE INDEX idx_pedido_status_historico_tenant_id ON pedido_status_historico (tenant_id);

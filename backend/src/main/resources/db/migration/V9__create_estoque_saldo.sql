-- Saldo materializado por SKU, derivado do ledger em estoque_movimento e atualizado
-- na mesma transacao do movimento (regra #2 do dominio). As CHECKs abaixo sao a rede
-- de seguranca contra overselling mesmo se algum caminho de codigo esquecer o lock
-- pessimista (regra #4).
CREATE TABLE estoque_saldo (
    variacao_id   UUID PRIMARY KEY REFERENCES variacao (id),
    tenant_id     UUID NOT NULL REFERENCES tenant (id),
    qtd_fisica    INTEGER NOT NULL DEFAULT 0,
    qtd_reservada INTEGER NOT NULL DEFAULT 0,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_estoque_saldo_fisica_nao_negativa CHECK (qtd_fisica >= 0),
    CONSTRAINT chk_estoque_saldo_reservada_nao_negativa CHECK (qtd_reservada >= 0),
    CONSTRAINT chk_estoque_saldo_disponivel_nao_negativo CHECK (qtd_fisica - qtd_reservada >= 0)
);

CREATE INDEX idx_estoque_saldo_tenant_id ON estoque_saldo (tenant_id);

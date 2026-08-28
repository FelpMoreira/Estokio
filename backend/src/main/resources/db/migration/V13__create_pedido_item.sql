-- Item do pedido, com preco e nome congelados no momento da compra (snapshot -- Decisao #8).
-- tenant_id foi adicionado aqui alem do que a spec original tinha (Decisao de kickoff): toda
-- tabela operacional precisa da coluna direta para a RLS funcionar sem depender de join.
CREATE TABLE pedido_item (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenant (id),
    pedido_id             UUID NOT NULL REFERENCES pedido (id),
    variacao_id           UUID NOT NULL REFERENCES variacao (id),
    quantidade            INTEGER NOT NULL CHECK (quantidade > 0),
    preco_unit_centavos   INTEGER NOT NULL CHECK (preco_unit_centavos >= 0),
    nome_snapshot         VARCHAR(200) NOT NULL,
    sku_snapshot          VARCHAR(60) NOT NULL
);

CREATE INDEX idx_pedido_item_pedido_id ON pedido_item (pedido_id);
CREATE INDEX idx_pedido_item_tenant_id ON pedido_item (tenant_id);

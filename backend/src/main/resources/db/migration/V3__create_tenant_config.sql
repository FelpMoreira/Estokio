-- Configuracao operacional de cada loja (frete, formas de pagamento, prazo de reserva).
CREATE TABLE tenant_config (
    tenant_id                    UUID PRIMARY KEY REFERENCES tenant (id),
    frete_fixo_centavos          INTEGER NOT NULL DEFAULT 0 CHECK (frete_fixo_centavos >= 0),
    frete_gratis_acima_centavos  INTEGER CHECK (frete_gratis_acima_centavos IS NULL OR frete_gratis_acima_centavos >= 0),
    formas_pagamento             JSONB NOT NULL DEFAULT '[]'::jsonb,
    minutos_reserva              INTEGER NOT NULL DEFAULT 30 CHECK (minutos_reserva > 0),
    dias_produto_parado          INTEGER NOT NULL DEFAULT 30 CHECK (dias_produto_parado > 0)
);

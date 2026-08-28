-- Row Level Security como defesa em profundidade (regra #5 do dominio): mesmo que um
-- repositorio esqueca o filtro de tenant_id, o banco barra. A aplicacao roda
-- `SET LOCAL app.tenant_id = ?` no inicio de cada transacao de rota de loja.
--
-- NAO tem RLS: plano, tenant, usuario, refresh_token.
--   - usuario precisa ser consultavel por email no login antes de saber o tenant
--     (SUPER_ADMIN e CLIENTE tem tenant_id NULL e sao globais).
--   - plano/tenant sao geridos pelo Super Admin via role app_platform (BYPASSRLS),
--     nao fazem sentido filtrados por si mesmos.
ALTER TABLE tenant_config ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_iso ON tenant_config
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE categoria ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_iso ON categoria
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE produto ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_iso ON produto
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE variacao ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_iso ON variacao
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE estoque_saldo ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_iso ON estoque_saldo
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE estoque_movimento ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_iso ON estoque_movimento
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE pedido ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_iso ON pedido
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE pedido_item ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_iso ON pedido_item
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE pedido_status_historico ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_iso ON pedido_status_historico
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE alerta ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_iso ON alerta
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

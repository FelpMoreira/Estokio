-- Privilegios finos por role (Decisao de kickoff #10). Isto so pode rodar depois que as
-- tabelas existem, e `migrator` (dono de tudo) e quem concede, por isso vive numa migration
-- Flyway em vez do script de init do container (que so cria os 3 roles).
--
-- app_user: usado pela aplicacao fora de /api/plataforma/**, sujeito as policies de RLS.
GRANT SELECT, INSERT, UPDATE, DELETE ON
    plano, tenant, tenant_config, usuario, refresh_token,
    categoria, produto, variacao, estoque_saldo,
    pedido, pedido_item, pedido_status_historico, alerta
TO app_user;

-- estoque_movimento e append-only: app_user so pode inserir e ler, nunca alterar/apagar
-- (blindagem 3 de 3, junto com a trigger e a ausencia de UPDATE/DELETE no repositorio).
GRANT SELECT, INSERT ON estoque_movimento TO app_user;
REVOKE UPDATE, DELETE ON estoque_movimento FROM app_user;

-- Sequences: toda tabela usa UUID default exceto estoque_movimento (BIGSERIAL), que
-- precisa de USAGE na sequence subjacente para o INSERT funcionar.
GRANT USAGE ON SEQUENCE estoque_movimento_id_seq TO app_user;

-- app_platform: BYPASSRLS, usado so nas rotas /api/plataforma/**, restrito a queries
-- agregadas pela camada de servico (o banco so garante que ele PODE ver tudo).
GRANT SELECT ON
    plano, tenant, tenant_config, usuario, refresh_token,
    categoria, produto, variacao, estoque_saldo, estoque_movimento,
    pedido, pedido_item, pedido_status_historico, alerta
TO app_platform;

-- Super Admin tambem cria/gerencia planos e tenants (aprovar/suspender loja).
GRANT INSERT, UPDATE ON plano, tenant TO app_platform;

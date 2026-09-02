-- Mesma blindagem do ledger de estoque (V11), aplicada aqui: pedido_status_historico e
-- o log de auditoria de quem/quando/por que todo pedido mudou de status, e precisa ser
-- append-only pelo mesmo motivo que o ledger precisa (achado na revisao da Fase 0).
CREATE FUNCTION bloquear_alteracao_pedido_status_historico()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'pedido_status_historico e append-only: % nao e permitido (id=%).',
        TG_OP, COALESCE(OLD.id, NULL);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pedido_status_historico_imutavel
    BEFORE UPDATE OR DELETE ON pedido_status_historico
    FOR EACH ROW
    EXECUTE FUNCTION bloquear_alteracao_pedido_status_historico();

-- Blindagem de privilegio (a mesma dada a estoque_movimento em V17): app_user so pode
-- inserir e ler, nunca alterar/apagar uma linha de historico.
REVOKE UPDATE, DELETE ON pedido_status_historico FROM app_user;

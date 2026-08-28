-- Blindagem 2 de 3 contra edicao do ledger (a 1a e nao ter UPDATE/DELETE no repositorio,
-- a 3a e o REVOKE de privilegio feito na migration de grants). Correcao de erro nunca
-- edita ou apaga uma linha: gera um novo movimento de estorno (estorna_movimento_id).
CREATE FUNCTION bloquear_alteracao_estoque_movimento()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'estoque_movimento e append-only: % nao e permitido (id=%). Use um movimento de estorno.',
        TG_OP, COALESCE(OLD.id, NULL);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_estoque_movimento_imutavel
    BEFORE UPDATE OR DELETE ON estoque_movimento
    FOR EACH ROW
    EXECUTE FUNCTION bloquear_alteracao_estoque_movimento();

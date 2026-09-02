package com.estokio.repository.auth;

import com.estokio.domain.user.RefreshToken;
import org.jdbi.v3.core.Jdbi;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code refresh_token} NAO tem RLS (mesma justificativa de {@code usuario}: precisa
 * ser consultavel antes de qualquer contexto de tenant estar disponivel). So o hash
 * do token e persistido (Decisao de kickoff #11) -- nunca o valor bruto.
 */
public final class RefreshTokenRepository {

    private static final String COLUNAS = "id, usuario_id, token_hash, expira_em, revogado_em, criado_em";

    private final Jdbi jdbi;

    public RefreshTokenRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public RefreshToken salvar(UUID usuarioId, String tokenHash, Instant expiraEm) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "INSERT INTO refresh_token (id, usuario_id, token_hash, expira_em) "
                                + "VALUES (gen_random_uuid(), :usuarioId, :tokenHash, :expiraEm) "
                                + "RETURNING " + COLUNAS)
                .bind("usuarioId", usuarioId)
                .bind("tokenHash", tokenHash)
                .bind("expiraEm", expiraEm)
                .mapTo(RefreshToken.class)
                .one());
    }

    public Optional<RefreshToken> buscarPorHash(String tokenHash) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT " + COLUNAS + " FROM refresh_token WHERE token_hash = :tokenHash")
                .bind("tokenHash", tokenHash)
                .mapTo(RefreshToken.class)
                .findOne());
    }

    /**
     * Valida e revoga o token numa unica instrucao (compare-and-swap via WHERE):
     * duas requisicoes concorrentes com o mesmo token bruto nunca revogam a mesma
     * linha duas vezes nem emitem dois pares de token a partir do mesmo refresh.
     * So retorna a linha se ela ainda estava valida no momento da revogacao.
     */
    public Optional<RefreshToken> consumirSeValido(String tokenHash, Instant agora) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "UPDATE refresh_token SET revogado_em = :agora "
                                + "WHERE token_hash = :tokenHash AND revogado_em IS NULL AND expira_em > :agora "
                                + "RETURNING " + COLUNAS)
                .bind("tokenHash", tokenHash)
                .bind("agora", agora)
                .mapTo(RefreshToken.class)
                .findOne());
    }

    /** Revogacao explicita (logout, rotacao no refresh, comprometimento). Nunca apaga a linha. */
    public void revogar(UUID id) {
        jdbi.useHandle(handle -> handle.execute(
                "UPDATE refresh_token SET revogado_em = now() WHERE id = ? AND revogado_em IS NULL", id));
    }
}

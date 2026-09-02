package com.estokio.domain.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token persistido e revogavel (Decisao de kickoff #11). {@code tokenHash} e
 * sempre o hash SHA-256 do token entregue ao cliente -- o texto puro nunca e gravado.
 */
public record RefreshToken(
        UUID id,
        UUID usuarioId,
        String tokenHash,
        Instant expiraEm,
        Instant revogadoEm,
        Instant criadoEm
) {
    public boolean valido(Instant agora) {
        return revogadoEm == null && expiraEm.isAfter(agora);
    }
}

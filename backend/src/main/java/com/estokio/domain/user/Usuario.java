package com.estokio.domain.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Usuario da plataforma. {@code tenantId} e {@code null} para SUPER_ADMIN e CLIENTE
 * (papeis globais, ver {@link Papel}). Nao tem RLS: o login precisa localizar o
 * usuario pelo email antes mesmo de saber o tenant.
 */
public record Usuario(
        UUID id,
        UUID tenantId,
        String nome,
        String email,
        String senhaHash,
        Papel papel,
        boolean ativo,
        Instant criadoEm
) {
}

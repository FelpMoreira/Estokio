package com.estokio.domain.tenant;

import java.time.Instant;
import java.util.UUID;

/**
 * Loja: unidade de isolamento multi-tenant. Toda tabela operacional carrega
 * {@code tenant_id} apontando para esta entidade.
 */
public record Tenant(
        UUID id,
        String nome,
        String slug,
        StatusTenant status,
        UUID planoId,
        Instant criadoEm
) {
}

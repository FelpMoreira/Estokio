package com.estokio.domain.tenant;

/**
 * Ciclo de vida de um tenant (loja) na plataforma.
 * Espelha o CHECK constraint da coluna {@code tenant.status}.
 */
public enum StatusTenant {
    PENDENTE,
    ATIVA,
    SUSPENSA,
    CANCELADA
}

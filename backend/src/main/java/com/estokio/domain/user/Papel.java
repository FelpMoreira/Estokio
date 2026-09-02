package com.estokio.domain.user;

/**
 * Papel do usuario na plataforma. Espelha o CHECK constraint de {@code usuario.papel}.
 * SUPER_ADMIN e CLIENTE sao globais (tenant_id NULL); ADMIN_LOJA e OPERADOR pertencem
 * a um tenant.
 */
public enum Papel {
    SUPER_ADMIN,
    ADMIN_LOJA,
    OPERADOR,
    CLIENTE
}

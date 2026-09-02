package com.estokio.domain.tenant;

import java.time.Instant;
import java.util.UUID;

/**
 * Plano contratado por um tenant: limites de uso da plataforma.
 * Enforcement dos limites (produtos, pedidos/mes, usuarios) e trabalho da Fase 2.
 */
public record Plan(
        UUID id,
        String nome,
        int maxProdutos,
        int maxPedidosMes,
        int maxUsuarios,
        int precoCentavos,
        boolean ativo,
        Instant criadoEm
) {
}

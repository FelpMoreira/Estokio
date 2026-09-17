package com.estokio.domain.inventory;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha do ledger append-only de estoque (ver [[Ledger de Estoque]]): fonte da
 * verdade de qualquer movimentacao, inclusive a entrada de estoque inicial de um
 * SKU recem-criado. Nunca atualizada nem apagada (trigger + REVOKE no banco, ver
 * V11__estoque_movimento_trigger_imutabilidade.sql e V17__grant_privileges_app_roles.sql).
 */
public record EstoqueMovimento(
        long id,
        UUID tenantId,
        UUID variacaoId,
        TipoMovimento tipo,
        int deltaFisico,
        int deltaReservado,
        String motivo,
        String origemTipo,
        UUID origemId,
        Long estornaMovimentoId,
        UUID usuarioId,
        Instant criadoEm
) {
}

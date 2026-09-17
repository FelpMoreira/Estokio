package com.estokio.domain.inventory;

import java.time.Instant;
import java.util.UUID;

/**
 * Saldo materializado por SKU (ver [[Ledger de Estoque]]): snapshot mantido na MESMA
 * transacao de cada {@link EstoqueMovimento}, nunca escrito diretamente por uma rota
 * de "editar quantidade" -- essa rota nao existe no dominio.
 */
public record EstoqueSaldo(
        UUID variacaoId,
        UUID tenantId,
        int qtdFisica,
        int qtdReservada,
        Instant atualizadoEm
) {

    public int disponivel() {
        return qtdFisica - qtdReservada;
    }
}

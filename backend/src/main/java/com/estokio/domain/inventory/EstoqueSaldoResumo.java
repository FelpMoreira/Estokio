package com.estokio.domain.inventory;

import java.util.UUID;

/**
 * Projecao de leitura de {@code estoque_saldo} + {@code variacao} + {@code produto},
 * usada por {@code GET /api/loja/estoque} e como resposta de
 * {@code POST /api/loja/estoque/entrada}. Nao e uma tabela; {@code disponivel} vem
 * calculado no proprio SQL ({@code qtd_fisica - qtd_reservada}).
 */
public record EstoqueSaldoResumo(
        UUID variacaoId,
        String sku,
        String nomeProduto,
        int qtdFisica,
        int qtdReservada,
        int disponivel,
        int pontoReposicao
) {
}

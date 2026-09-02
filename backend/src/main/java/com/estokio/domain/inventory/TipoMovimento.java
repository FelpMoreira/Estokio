package com.estokio.domain.inventory;

/**
 * Tipo de um movimento no ledger de estoque. Espelha o CHECK constraint de
 * {@code estoque_movimento.tipo}. Cada tipo carrega um par de deltas
 * (fisico, reservado) conforme a tabela da secao 4.1 da spec do projeto.
 */
public enum TipoMovimento {
    ENTRADA,
    SAIDA,
    RESERVA,
    LIBERACAO,
    BAIXA,
    DEVOLUCAO,
    AJUSTE,
    INVENTARIO
}

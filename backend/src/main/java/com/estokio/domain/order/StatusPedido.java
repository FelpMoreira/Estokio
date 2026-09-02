package com.estokio.domain.order;

/**
 * Estados possiveis de um pedido. Espelha o CHECK constraint de {@code pedido.status}.
 * A maquina de estados completa (transicoes permitidas, efeito no estoque) e
 * implementada na Fase 1 -- aqui so o vocabulario, para ja existir um tipo forte
 * em vez de String solta circulando pelo codigo.
 */
public enum StatusPedido {
    PENDENTE,
    CONFIRMADO,
    SEPARANDO,
    ENVIADO,
    ENTREGUE,
    CANCELADO,
    EXPIRADO,
    DEVOLVIDO
}

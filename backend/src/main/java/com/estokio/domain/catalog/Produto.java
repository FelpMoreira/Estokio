package com.estokio.domain.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade comercial (nome, descricao). Nao tem estoque proprio -- isso vive na
 * {@link Variacao} (SKU), ver [[Modelo de Dados]]. {@code categoriaId} e nulo: o
 * CRUD de categoria nao existe nesta sub-entrega (Fase 1, item 2), so o schema ja
 * suporta o campo.
 */
public record Produto(
        UUID id,
        UUID tenantId,
        UUID categoriaId,
        String nome,
        String descricao,
        boolean ativo,
        Instant criadoEm
) {
}

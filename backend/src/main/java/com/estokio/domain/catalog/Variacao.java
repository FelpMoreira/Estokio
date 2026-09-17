package com.estokio.domain.catalog;

import java.util.Map;
import java.util.UUID;

/**
 * Variacao / SKU: unidade que realmente tem preco e estoque (ver [[Modelo de Dados]]).
 * Nasce sempre com saldo zerado em {@code estoque_saldo} -- nao existe campo de
 * quantidade aqui de proposito (regra de ouro do dominio: quantidade so muda via
 * ledger em {@code estoque_movimento}).
 *
 * <p>{@code atributos} espelha a coluna {@code jsonb} (ex: {@code {"tamanho":"M","cor":"azul"}})
 * como {@code Map<String, String>} -- {@link com.estokio.repository.catalog.VariacaoRepository}
 * serializa/parseia manualmente com Jackson, ja que o Jdbi nao tem o plugin de Json
 * instalado neste projeto (ver {@code TenantServiceTest}, que le outro jsonb via
 * {@code ::text}).</p>
 */
public record Variacao(
        UUID id,
        UUID tenantId,
        UUID produtoId,
        String sku,
        Map<String, String> atributos,
        int precoCentavos,
        int pontoReposicao,
        boolean ativo
) {
}

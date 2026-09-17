package com.estokio.domain.catalog;

import java.util.List;
import java.util.UUID;

/**
 * Projecao de leitura de {@code GET /api/loja/produtos}: o produto com suas
 * variacoes/SKUs embutidas, para a tela de Produtos nao precisar de uma segunda
 * chamada por produto. Nao e uma tabela.
 */
public record ProdutoComVariacoes(
        UUID id,
        String nome,
        String descricao,
        boolean ativo,
        List<Variacao> variacoes
) {
}

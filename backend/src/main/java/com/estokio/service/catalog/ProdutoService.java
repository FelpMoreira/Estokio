package com.estokio.service.catalog;

import com.estokio.domain.catalog.Produto;
import com.estokio.domain.catalog.ProdutoComVariacoes;
import com.estokio.domain.catalog.Variacao;
import com.estokio.exception.ApiException;
import com.estokio.repository.catalog.ProdutoRepository;
import com.estokio.repository.catalog.VariacaoRepository;
import com.estokio.repository.inventory.EstoqueRepository;
import com.estokio.security.TenantContext;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.postgresql.util.PSQLException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cadastro de produto/variacao (SKU) da Fase 1, item 2 -- ver [[Fase 1 - MVP]]. Roda
 * inteiramente via {@code appUserJdbi} (sujeito a RLS): toda transacao comeca com
 * {@link TenantContext#aplicarNaTransacao}, exigido pelas policies de
 * {@code produto}/{@code variacao}/{@code estoque_saldo} (ver [[Multi-Tenancy e RLS]]).
 *
 * <p><b>Regra de ouro do dominio:</b> uma variacao nasce com saldo zerado em
 * {@code estoque_saldo} na MESMA transacao da sua criacao -- nao existe campo de
 * "quantidade inicial" aqui. Estoque so entra via ledger, ver
 * {@link com.estokio.service.inventory.EstoqueService#registrarEntrada}.</p>
 */
public final class ProdutoService {

    /** Codigo do postgres para "unique_violation" -- rede de seguranca contra corrida na checagem previa de SKU. */
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    private final Jdbi appUserJdbi;
    private final ProdutoRepository produtoRepository;
    private final VariacaoRepository variacaoRepository;
    private final EstoqueRepository estoqueRepository;

    public ProdutoService(Jdbi appUserJdbi, ProdutoRepository produtoRepository, VariacaoRepository variacaoRepository,
                           EstoqueRepository estoqueRepository) {
        this.appUserJdbi = appUserJdbi;
        this.produtoRepository = produtoRepository;
        this.variacaoRepository = variacaoRepository;
        this.estoqueRepository = estoqueRepository;
    }

    public Produto criarProduto(String nome, String descricao) {
        UUID tenantId = TenantContext.tenantIdObrigatorio();
        return appUserJdbi.inTransaction(handle -> {
            TenantContext.aplicarNaTransacao(handle);
            // categoria_id fica de fora nesta sub-entrega: nao ha CRUD de categoria na
            // spec da Fase 1 (ver escopo da tarefa), o campo so existe no schema.
            return produtoRepository.inserir(handle, tenantId, null, nome, descricao);
        });
    }

    public List<ProdutoComVariacoes> listarProdutos() {
        UUID tenantId = TenantContext.tenantIdObrigatorio();
        return appUserJdbi.inTransaction(handle -> {
            TenantContext.aplicarNaTransacao(handle);
            List<Produto> produtos = produtoRepository.listarPorTenant(handle, tenantId);
            List<UUID> produtoIds = produtos.stream().map(Produto::id).toList();
            Map<UUID, List<Variacao>> variacoesPorProduto = variacaoRepository
                    .listarPorProdutoIds(handle, tenantId, produtoIds).stream()
                    .collect(Collectors.groupingBy(Variacao::produtoId));

            return produtos.stream()
                    .map(produto -> new ProdutoComVariacoes(
                            produto.id(), produto.nome(), produto.descricao(), produto.ativo(),
                            variacoesPorProduto.getOrDefault(produto.id(), List.of())))
                    .toList();
        });
    }

    /**
     * Cria a variacao/SKU e a linha {@code estoque_saldo} zerada correspondente numa
     * unica transacao. A checagem previa de SKU duplicado + captura de
     * {@link UnableToExecuteStatementException} segue a mesma disciplina de
     * {@code TenantService.criarLoja} (Fase 1, item 1).
     */
    public Variacao criarVariacao(UUID produtoId, String sku, Map<String, String> atributos, int precoCentavos,
                                   int pontoReposicao) {
        UUID tenantId = TenantContext.tenantIdObrigatorio();
        try {
            return appUserJdbi.inTransaction(handle -> {
                TenantContext.aplicarNaTransacao(handle);

                produtoRepository.buscarPorId(handle, tenantId, produtoId)
                        .orElseThrow(() -> ApiException.naoEncontrado(
                                "PRODUTO_NAO_ENCONTRADO", "Produto nao encontrado."));

                if (variacaoRepository.existeSku(handle, tenantId, sku)) {
                    throw ApiException.conflito("SKU_JA_USADO", "Ja existe uma variacao com este SKU.");
                }

                Variacao variacao = variacaoRepository.inserir(
                        handle, tenantId, produtoId, sku, atributos, precoCentavos, pontoReposicao);
                estoqueRepository.criarSaldoZerado(handle, tenantId, variacao.id());
                return variacao;
            });
        } catch (UnableToExecuteStatementException e) {
            throw traduzirViolacaoDeUnicidade(e);
        }
    }

    /** PATCH parcial: nunca toca estoque, so preco/ponto de reposicao/ativo. */
    public Variacao atualizarVariacao(UUID id, Integer precoCentavos, Integer pontoReposicao, Boolean ativo) {
        UUID tenantId = TenantContext.tenantIdObrigatorio();
        return appUserJdbi.inTransaction(handle -> {
            TenantContext.aplicarNaTransacao(handle);
            return variacaoRepository.atualizar(handle, tenantId, id, precoCentavos, pontoReposicao, ativo)
                    .orElseThrow(() -> ApiException.naoEncontrado("VARIACAO_NAO_ENCONTRADA", "Variacao nao encontrada."));
        });
    }

    private ApiException traduzirViolacaoDeUnicidade(UnableToExecuteStatementException e) {
        if (e.getCause() instanceof PSQLException causa
                && SQLSTATE_UNIQUE_VIOLATION.equals(causa.getSQLState())) {
            return ApiException.conflito("SKU_JA_USADO", "Ja existe uma variacao com este SKU.");
        }
        throw new IllegalStateException("Falha inesperada ao criar variacao.", e);
    }
}

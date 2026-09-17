package com.estokio.service.catalog;

import com.estokio.PostgresTestBase;
import com.estokio.TestFixtures;
import com.estokio.domain.catalog.Produto;
import com.estokio.domain.catalog.Variacao;
import com.estokio.domain.inventory.EstoqueSaldoResumo;
import com.estokio.domain.user.Papel;
import com.estokio.domain.user.Usuario;
import com.estokio.exception.ApiException;
import com.estokio.repository.catalog.ProdutoRepository;
import com.estokio.repository.catalog.VariacaoRepository;
import com.estokio.repository.inventory.EstoqueRepository;
import com.estokio.security.TenantContext;
import com.estokio.service.inventory.EstoqueService;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sub-entrega 1b da Fase 1 (ver Notas de Implementacao - Fase 1): Admin/Operador
 * cadastra produto/variacao (SKU) via {@link ProdutoService}. Ate esta classe, o fluxo
 * so tinha validacao manual -- aqui roda contra Postgres real (Testcontainers), sem
 * mock, confirmando direto no banco (nao so no retorno do metodo) que a variacao nasce
 * com saldo zerado na mesma transacao, que a unicidade de SKU e por tenant (nao
 * global), e que o PATCH parcial nunca mexe em estoque.
 */
class ProdutoServiceTest extends PostgresTestBase {

    private static Jdbi appUser;
    private static Jdbi migrator;
    private static ProdutoService produtoService;
    private static EstoqueService estoqueService;

    @BeforeAll
    static void setUp() {
        appUser = appUserJdbi();
        migrator = migratorJdbi();

        ProdutoRepository produtoRepository = new ProdutoRepository();
        VariacaoRepository variacaoRepository = new VariacaoRepository();
        EstoqueRepository estoqueRepository = new EstoqueRepository();
        produtoService = new ProdutoService(appUser, produtoRepository, variacaoRepository, estoqueRepository);
        estoqueService = new EstoqueService(appUser, estoqueRepository);
    }

    // ---------------------------------------------------------------- fixtures/helpers locais

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static UUID novoTenant() {
        return TestFixtures.criarTenant(migrator);
    }

    /**
     * Roda a acao com {@link TenantContext} populado como o {@link TenantMiddleware}
     * faria numa requisicao real, sempre limpando ao final -- mesmo em caso de excecao,
     * para nunca vazar identidade entre chamadas dentro do mesmo metodo de teste. O
     * usuario e sempre uma linha real de {@code usuario} (nao um UUID aleatorio): alguns
     * caminhos (via {@link EstoqueService}) gravam {@code usuario_id} num INSERT com FK
     * para {@code usuario}, entao um id nao-existente derrubaria a escrita com violacao
     * de chave estrangeira em vez de exercitar o comportamento sob teste.
     */
    private static <T> T comoUsuarioDaLoja(UUID tenantId, Papel papel, Supplier<T> acao) {
        Usuario usuario = TestFixtures.criarUsuarioAtivo(migrator, tenantId, papel, "senha-teste-123");
        TenantContext.definir(usuario.id(), papel, tenantId);
        try {
            return acao.get();
        } finally {
            TenantContext.limpar();
        }
    }

    private static void comoUsuarioDaLoja(UUID tenantId, Papel papel, Runnable acao) {
        comoUsuarioDaLoja(tenantId, papel, () -> {
            acao.run();
            return null;
        });
    }

    private record LinhaSaldo(int qtdFisica, int qtdReservada) {
    }

    private static LinhaSaldo buscarSaldo(UUID variacaoId) {
        return migrator.withHandle(h -> h.createQuery(
                        "SELECT qtd_fisica, qtd_reservada FROM estoque_saldo WHERE variacao_id = :id")
                .bind("id", variacaoId)
                .map((rs, ctx) -> new LinhaSaldo(rs.getInt("qtd_fisica"), rs.getInt("qtd_reservada")))
                .one());
    }

    private static int contarVariacoesComSku(String sku) {
        return migrator.withHandle(h -> h.createQuery("SELECT COUNT(*) FROM variacao WHERE sku = :sku")
                .bind("sku", sku)
                .mapTo(Integer.class)
                .one());
    }

    // ---------------------------------------------------------------- caminho feliz

    @Test
    void criarProdutoEVariacaoComSucessoNasceComSaldoZeradoNaMesmaTransacao() {
        UUID tenantId = novoTenant();
        String sku = "SKU-" + sufixo();

        Variacao variacao = comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA, () -> {
            Produto produto = produtoService.criarProduto("Camiseta Basica", "100% algodao");
            assertNotNull(produto.id());
            assertTrue(produto.ativo());

            return produtoService.criarVariacao(
                    produto.id(), sku, Map.of("tamanho", "M", "cor", "azul"), 4990, 5);
        });

        assertNotNull(variacao.id());
        assertEquals(sku, variacao.sku());
        assertEquals(4990, variacao.precoCentavos());
        assertEquals(5, variacao.pontoReposicao());
        assertEquals(Map.of("tamanho", "M", "cor", "azul"), variacao.atributos());
        assertTrue(variacao.ativo());

        // Confirma direto no banco -- nao so no retorno do metodo.
        LinhaSaldo saldo = buscarSaldo(variacao.id());
        assertEquals(0, saldo.qtdFisica(), "variacao recem-criada deve nascer com qtd_fisica zerada");
        assertEquals(0, saldo.qtdReservada(), "variacao recem-criada deve nascer com qtd_reservada zerada");
    }

    // ---------------------------------------------------------------- SKU duplicado

    @Test
    void skuDuplicadoNoMesmoTenantFalhaComSkuJaUsadoESoUmaVariacaoPersiste() {
        UUID tenantId = novoTenant();
        String skuDisputado = "SKU-" + sufixo();

        comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA, () -> {
            Produto produto = produtoService.criarProduto("Produto Um", null);
            produtoService.criarVariacao(produto.id(), skuDisputado, Map.of(), 1000, 0);
        });

        ApiException erro = assertThrows(ApiException.class, () -> comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA, () -> {
            Produto outroProduto = produtoService.criarProduto("Produto Dois", null);
            produtoService.criarVariacao(outroProduto.id(), skuDisputado, Map.of(), 2000, 0);
        }));

        assertEquals(409, erro.status());
        assertEquals("SKU_JA_USADO", erro.codigo());
        assertEquals(1, contarVariacoesComSku(skuDisputado));
    }

    @Test
    void skuRepetidoEmTenantsDiferentesFuncionaNormalmenteUnicidadeENaoGlobal() {
        String skuCompartilhado = "SKU-" + sufixo();
        UUID tenantA = novoTenant();
        UUID tenantB = novoTenant();

        Variacao variacaoA = comoUsuarioDaLoja(tenantA, Papel.ADMIN_LOJA, () -> {
            Produto produto = produtoService.criarProduto("Produto Tenant A", null);
            return produtoService.criarVariacao(produto.id(), skuCompartilhado, Map.of(), 1000, 0);
        });

        Variacao variacaoB = comoUsuarioDaLoja(tenantB, Papel.ADMIN_LOJA, () -> {
            Produto produto = produtoService.criarProduto("Produto Tenant B", null);
            return produtoService.criarVariacao(produto.id(), skuCompartilhado, Map.of(), 1500, 0);
        });

        assertNotNull(variacaoA.id());
        assertNotNull(variacaoB.id());
        assertFalse(variacaoA.id().equals(variacaoB.id()));
        assertEquals(2, contarVariacoesComSku(skuCompartilhado),
                "unicidade de sku e UNIQUE(tenant_id, sku), nao global");
    }

    // ---------------------------------------------------------------- produto inexistente

    @Test
    void criarVariacaoParaProdutoInexistenteFalhaComProdutoNaoEncontrado() {
        UUID tenantId = novoTenant();

        ApiException erro = assertThrows(ApiException.class, () -> comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA,
                () -> produtoService.criarVariacao(UUID.randomUUID(), "SKU-" + sufixo(), Map.of(), 1000, 0)));

        assertEquals(404, erro.status());
        assertEquals("PRODUTO_NAO_ENCONTRADO", erro.codigo());
    }

    @Test
    void criarVariacaoParaProdutoDeOutroTenantFalhaComProdutoNaoEncontrado() {
        UUID tenantA = novoTenant();
        UUID tenantB = novoTenant();

        Produto produtoDeA = comoUsuarioDaLoja(tenantA, Papel.ADMIN_LOJA,
                () -> produtoService.criarProduto("Produto So De A", null));

        ApiException erro = assertThrows(ApiException.class, () -> comoUsuarioDaLoja(tenantB, Papel.ADMIN_LOJA,
                () -> produtoService.criarVariacao(produtoDeA.id(), "SKU-" + sufixo(), Map.of(), 1000, 0)));

        assertEquals(404, erro.status());
        assertEquals("PRODUTO_NAO_ENCONTRADO", erro.codigo());
    }

    // ---------------------------------------------------------------- PATCH parcial

    @Test
    void patchVariacaoAtualizaSoPrecoSemZerarPontoDeReposicaoNemMexerNoSaldo() {
        UUID tenantId = novoTenant();

        Variacao criada = comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA, () -> {
            Produto produto = produtoService.criarProduto("Produto Patch", null);
            return produtoService.criarVariacao(produto.id(), "SKU-" + sufixo(), Map.of(), 1000, 7);
        });

        // Da entrada de estoque real para o saldo nao ficar em zero -- se o PATCH mexesse
        // em estoque_saldo por engano, um saldo em zero nao revelaria o bug (0 == 0).
        EstoqueSaldoResumo saldoAntes = comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA,
                () -> estoqueService.registrarEntrada(criada.id(), 15, "carga inicial"));
        assertEquals(15, saldoAntes.qtdFisica());
        assertEquals(0, saldoAntes.qtdReservada());

        Variacao atualizada = comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA,
                () -> produtoService.atualizarVariacao(criada.id(), 3333, null, null));

        assertEquals(3333, atualizada.precoCentavos());
        assertEquals(7, atualizada.pontoReposicao(), "PATCH parcial nao pode zerar campo nao enviado");
        assertTrue(atualizada.ativo());

        LinhaSaldo saldoDepois = buscarSaldo(criada.id());
        assertEquals(15, saldoDepois.qtdFisica(), "PATCH de variacao nunca deve mexer em estoque_saldo");
        assertEquals(0, saldoDepois.qtdReservada());
    }

    // ---------------------------------------------------------------- isolamento multi-tenant (RLS)

    @Test
    void atualizarVariacaoDeOutroTenantRetorna404NaoEncontradaEDadoOriginalPermaneceIntacto() {
        UUID tenantA = novoTenant();
        UUID tenantB = novoTenant();

        Variacao variacaoDeA = comoUsuarioDaLoja(tenantA, Papel.ADMIN_LOJA, () -> {
            Produto produto = produtoService.criarProduto("Produto Isolado", null);
            return produtoService.criarVariacao(produto.id(), "SKU-" + sufixo(), Map.of(), 5000, 3);
        });

        ApiException erro = assertThrows(ApiException.class, () -> comoUsuarioDaLoja(tenantB, Papel.OPERADOR,
                () -> produtoService.atualizarVariacao(variacaoDeA.id(), 9999, null, null)));

        assertEquals(404, erro.status(), "vazamento de tenant nunca pode se manifestar como 403 nem sucesso");
        assertEquals("VARIACAO_NAO_ENCONTRADA", erro.codigo());

        Variacao aindaDeA = comoUsuarioDaLoja(tenantA, Papel.ADMIN_LOJA,
                () -> produtoService.atualizarVariacao(variacaoDeA.id(), null, null, null));
        assertEquals(5000, aindaDeA.precoCentavos(), "tentativa do tenant B nao pode ter alterado o preco de A");
    }
}

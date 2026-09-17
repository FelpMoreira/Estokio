package com.estokio.service.inventory;

import com.estokio.PostgresTestBase;
import com.estokio.TestFixtures;
import com.estokio.domain.inventory.EstoqueSaldoResumo;
import com.estokio.domain.user.Papel;
import com.estokio.domain.user.Usuario;
import com.estokio.exception.ApiException;
import com.estokio.repository.inventory.EstoqueRepository;
import com.estokio.security.TenantContext;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Sub-entrega 1b da Fase 1 (ver Notas de Implementacao - Fase 1): entrada de estoque
 * pelo ledger via {@link EstoqueService}. Roda contra Postgres real (Testcontainers),
 * sem mock, confirmando direto no banco que {@code estoque_movimento} e append-only e
 * que {@code estoque_saldo} e atualizado atomicamente com o movimento -- inclusive sob
 * concorrencia real no lock pessimista ({@code SELECT ... FOR UPDATE}), a garantia
 * central desta sub-entrega.
 */
class EstoqueServiceTest extends PostgresTestBase {

    private static Jdbi appUser;
    private static Jdbi migrator;
    private static EstoqueService estoqueService;

    @BeforeAll
    static void setUp() {
        appUser = appUserJdbi();
        migrator = migratorJdbi();
        estoqueService = new EstoqueService(appUser, new EstoqueRepository());
    }

    // ---------------------------------------------------------------- fixtures/helpers locais

    private static UUID novoTenant() {
        return TestFixtures.criarTenant(migrator);
    }

    private static UUID novaVariacaoComSaldoZerado(UUID tenantId) {
        return TestFixtures.criarVariacaoComSaldo(migrator, tenantId, 0, 0);
    }

    /**
     * O usuario e sempre uma linha real de {@code usuario} (nao um UUID aleatorio):
     * {@code estoque_movimento.usuario_id} tem FK para {@code usuario}, entao um id
     * inexistente derrubaria o INSERT com violacao de chave estrangeira em vez de
     * exercitar o comportamento sob teste.
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

    private record LinhaSaldo(int qtdFisica, int qtdReservada) {
    }

    private static LinhaSaldo buscarSaldo(UUID variacaoId) {
        return migrator.withHandle(h -> h.createQuery(
                        "SELECT qtd_fisica, qtd_reservada FROM estoque_saldo WHERE variacao_id = :id")
                .bind("id", variacaoId)
                .map((rs, ctx) -> new LinhaSaldo(rs.getInt("qtd_fisica"), rs.getInt("qtd_reservada")))
                .one());
    }

    private record LinhaMovimento(String tipo, int deltaFisico, int deltaReservado, String motivo) {
    }

    private static List<LinhaMovimento> listarMovimentos(UUID variacaoId) {
        return migrator.withHandle(h -> h.createQuery("""
                        SELECT tipo, delta_fisico, delta_reservado, motivo FROM estoque_movimento
                        WHERE variacao_id = :id ORDER BY criado_em ASC, id ASC
                        """)
                .bind("id", variacaoId)
                .map((rs, ctx) -> new LinhaMovimento(
                        rs.getString("tipo"), rs.getInt("delta_fisico"), rs.getInt("delta_reservado"), rs.getString("motivo")))
                .list());
    }

    // ---------------------------------------------------------------- caminho feliz

    @Test
    void entradaDeEstoqueGravaMovimentoEAtualizaSaldoNaMesmaTransacao() {
        UUID tenantId = novoTenant();
        UUID variacaoId = novaVariacaoComSaldoZerado(tenantId);

        EstoqueSaldoResumo resumo = comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA,
                () -> estoqueService.registrarEntrada(variacaoId, 10, "compra inicial"));

        assertEquals(10, resumo.qtdFisica());
        assertEquals(0, resumo.qtdReservada());
        assertEquals(10, resumo.disponivel());

        LinhaSaldo saldo = buscarSaldo(variacaoId);
        assertEquals(10, saldo.qtdFisica());
        assertEquals(0, saldo.qtdReservada());

        List<LinhaMovimento> movimentos = listarMovimentos(variacaoId);
        assertEquals(1, movimentos.size());
        assertEquals("ENTRADA", movimentos.get(0).tipo());
        assertEquals(10, movimentos.get(0).deltaFisico());
        assertEquals(0, movimentos.get(0).deltaReservado());
        assertEquals("compra inicial", movimentos.get(0).motivo());
    }

    @Test
    void duasEntradasSequenciaisAcumulamEmVezDeSobrescrever() {
        UUID tenantId = novoTenant();
        UUID variacaoId = novaVariacaoComSaldoZerado(tenantId);

        comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA, () -> estoqueService.registrarEntrada(variacaoId, 10, "lote 1"));
        EstoqueSaldoResumo apos = comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA,
                () -> estoqueService.registrarEntrada(variacaoId, 5, "lote 2"));

        assertEquals(15, apos.qtdFisica(), "segunda entrada deveria acumular sobre a primeira, nao sobrescrever");

        LinhaSaldo saldo = buscarSaldo(variacaoId);
        assertEquals(15, saldo.qtdFisica());
        assertEquals(2, listarMovimentos(variacaoId).size());
    }

    // ---------------------------------------------------------------- quantidade invalida

    @Test
    void quantidadeZeroFalhaComQuantidadeInvalidaSemGravarNada() {
        UUID tenantId = novoTenant();
        UUID variacaoId = novaVariacaoComSaldoZerado(tenantId);

        ApiException erro = assertThrows(ApiException.class, () -> comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA,
                () -> estoqueService.registrarEntrada(variacaoId, 0, null)));

        assertEquals(400, erro.status());
        assertEquals("QUANTIDADE_INVALIDA", erro.codigo());
        assertEquals(0, buscarSaldo(variacaoId).qtdFisica());
        assertEquals(0, listarMovimentos(variacaoId).size());
    }

    @Test
    void quantidadeNegativaFalhaComQuantidadeInvalidaSemGravarNada() {
        UUID tenantId = novoTenant();
        UUID variacaoId = novaVariacaoComSaldoZerado(tenantId);

        ApiException erro = assertThrows(ApiException.class, () -> comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA,
                () -> estoqueService.registrarEntrada(variacaoId, -3, null)));

        assertEquals(400, erro.status());
        assertEquals("QUANTIDADE_INVALIDA", erro.codigo());
        assertEquals(0, buscarSaldo(variacaoId).qtdFisica());
        assertEquals(0, listarMovimentos(variacaoId).size());
    }

    // ---------------------------------------------------------------- isolamento multi-tenant (RLS)

    @Test
    void entradaEmVariacaoDeOutroTenantRetorna404ESaldoOriginalPermaneceIntacto() {
        UUID tenantA = novoTenant();
        UUID tenantB = novoTenant();
        UUID variacaoDeA = novaVariacaoComSaldoZerado(tenantA);

        ApiException erro = assertThrows(ApiException.class, () -> comoUsuarioDaLoja(tenantB, Papel.OPERADOR,
                () -> estoqueService.registrarEntrada(variacaoDeA, 50, "tentativa indevida")));

        assertEquals(404, erro.status(), "vazamento de tenant nunca pode se manifestar como 403 nem sucesso");
        assertEquals("VARIACAO_NAO_ENCONTRADA", erro.codigo());
        assertEquals(0, buscarSaldo(variacaoDeA).qtdFisica());
        assertEquals(0, listarMovimentos(variacaoDeA).size());
    }

    // ---------------------------------------------------------------- concorrencia real no lock pessimista

    /**
     * O teste mais importante desta sub-entrega: duas entradas de estoque simultaneas no
     * MESMO SKU, disparadas por threads reais sincronizadas numa {@link CyclicBarrier} para
     * maximizar a chance de as duas transacoes disputarem a mesma linha de
     * {@code estoque_saldo} ao mesmo tempo. {@link EstoqueRepository#buscarParaAtualizar}
     * faz {@code SELECT ... FOR UPDATE}, entao a segunda transacao deve bloquear ate a
     * primeira commitar -- sem isso, as duas leriam o mesmo {@code qtd_fisica} inicial e uma
     * das duas entradas seria perdida (lost update). Reproduz a disciplina de
     * {@code TenantServiceTest#duasCriacoesConcorrentesComMesmoSlugSoUmaTemSucesso}.
     */
    @Test
    void duasEntradasConcorrentesNoMesmoSkuSaldoFinalFechaComASomaExataSemPerdaDeIncremento() throws Exception {
        UUID tenantId = novoTenant();
        UUID variacaoId = novaVariacaoComSaldoZerado(tenantId);
        int quantidadeA = 30;
        int quantidadeB = 70;

        CyclicBarrier largada = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<EstoqueSaldoResumo> futuroA = executor.submit(
                    tarefaEntrada(largada, tenantId, variacaoId, quantidadeA, "entrada concorrente A"));
            Future<EstoqueSaldoResumo> futuroB = executor.submit(
                    tarefaEntrada(largada, tenantId, variacaoId, quantidadeB, "entrada concorrente B"));

            EstoqueSaldoResumo resultadoA = obter(futuroA);
            EstoqueSaldoResumo resultadoB = obter(futuroB);
            assertNotNull(resultadoA);
            assertNotNull(resultadoB);

            LinhaSaldo saldoFinal = buscarSaldo(variacaoId);
            int esperado = quantidadeA + quantidadeB;
            assertEquals(esperado, saldoFinal.qtdFisica(),
                    "saldo final deveria ser a soma exata das duas entradas concorrentes (sem lost update)");
            assertEquals(0, saldoFinal.qtdReservada());

            List<LinhaMovimento> movimentos = listarMovimentos(variacaoId);
            assertEquals(2, movimentos.size(), "as duas entradas concorrentes devem gerar 2 movimentos, nunca 1");
            int somaDeltas = movimentos.stream().mapToInt(LinhaMovimento::deltaFisico).sum();
            assertEquals(esperado, somaDeltas, "soma dos deltas do ledger deve bater com o saldo final");
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<EstoqueSaldoResumo> tarefaEntrada(
            CyclicBarrier largada, UUID tenantId, UUID variacaoId, int quantidade, String motivo) {
        return () -> {
            largada.await(10, TimeUnit.SECONDS);
            return comoUsuarioDaLoja(tenantId, Papel.ADMIN_LOJA,
                    () -> estoqueService.registrarEntrada(variacaoId, quantidade, motivo));
        };
    }

    private static EstoqueSaldoResumo obter(Future<EstoqueSaldoResumo> futuro) {
        try {
            return futuro.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            fail("Excecao inesperada na chamada concorrente: " + e.getCause(), e.getCause());
            return null; // inalcancavel
        } catch (InterruptedException | java.util.concurrent.TimeoutException e) {
            fail("Chamada concorrente nao terminou a tempo: " + e);
            return null; // inalcancavel
        }
    }
}

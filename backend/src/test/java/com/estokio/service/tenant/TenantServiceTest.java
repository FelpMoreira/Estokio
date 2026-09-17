package com.estokio.service.tenant;

import com.estokio.PostgresTestBase;
import com.estokio.TestFixtures;
import com.estokio.domain.tenant.StatusTenant;
import com.estokio.domain.tenant.Tenant;
import com.estokio.domain.user.Papel;
import com.estokio.domain.user.Usuario;
import com.estokio.exception.ApiException;
import com.estokio.repository.auth.UsuarioRepository;
import com.estokio.repository.tenant.PlanoRepository;
import com.estokio.repository.tenant.TenantRepository;
import com.estokio.security.PasswordEncoder;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Sub-entrega 1a da Fase 1 (ver Notas de Implementacao - Fase 1): Super Admin cria
 * loja + admin inicial via {@link TenantService#criarLoja}. Ate esta classe, o fluxo
 * so tinha validacao manual (curl + Chrome) -- aqui ele roda contra Postgres real
 * (Testcontainers), sem nenhum mock, confirmando direto no banco (nao so no retorno
 * do metodo) que as 3 tabelas (tenant/tenant_config/usuario) nascem ou falham juntas.
 */
class TenantServiceTest extends PostgresTestBase {

    private static final String SENHA_ADMIN = "senha-admin-123";

    private static Jdbi appPlatform;
    private static Jdbi migrator;
    private static TenantService tenantService;

    @BeforeAll
    static void setUp() {
        appPlatform = appPlatformJdbi();
        migrator = migratorJdbi();

        TenantRepository tenantRepository = new TenantRepository(appPlatform);
        PlanoRepository planoRepository = new PlanoRepository(appPlatform);
        UsuarioRepository usuarioRepository = new UsuarioRepository(appPlatform);
        tenantService = new TenantService(appPlatform, tenantRepository, planoRepository, usuarioRepository);
    }

    // ---------------------------------------------------------------- fixtures locais

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static UUID planoAtivo() {
        return TestFixtures.criarPlano(migrator, "Plano Ativo " + sufixo());
    }

    private static UUID planoInativo() {
        return migrator.withHandle(handle -> handle.createQuery("""
                        INSERT INTO plano (id, nome, max_produtos, max_pedidos_mes, max_usuarios, preco_centavos, ativo)
                        VALUES (gen_random_uuid(), :nome, 100, 1000, 5, 0, false)
                        RETURNING id
                        """)
                .bind("nome", "Plano Inativo " + sufixo())
                .mapTo(UUID.class)
                .one());
    }

    private static String slugUnico() {
        return "loja-teste-" + sufixo();
    }

    private static String emailUnico() {
        return "admin-" + sufixo() + "@estokio-test.dev";
    }

    /** Insere um usuario com email explicito (TestFixtures.criarUsuario sempre gera email aleatorio). */
    private static Usuario criarUsuarioComEmail(UUID tenantId, Papel papel, String email) {
        UsuarioRepository repositorio = new UsuarioRepository(migrator);
        return repositorio.inserir(new Usuario(
                UUID.randomUUID(), tenantId, "Usuario Fixture " + sufixo(), email,
                PasswordEncoder.hash("senha-fixture-123"), papel, true, Instant.now()));
    }

    private static int contarTenants() {
        return migrator.withHandle(h -> h.createQuery("SELECT COUNT(*) FROM tenant").mapTo(Integer.class).one());
    }

    private static int contarTenantsComSlug(String slug) {
        return migrator.withHandle(h -> h.createQuery("SELECT COUNT(*) FROM tenant WHERE slug = :slug")
                .bind("slug", slug).mapTo(Integer.class).one());
    }

    private static int contarUsuariosComEmail(String email) {
        return migrator.withHandle(h -> h.createQuery("SELECT COUNT(*) FROM usuario WHERE email = :email")
                .bind("email", email).mapTo(Integer.class).one());
    }

    private record LinhaTenantConfig(int freteFixoCentavos, String formasPagamento, int minutosReserva,
                                      int diasProdutoParado) {
    }

    private static LinhaTenantConfig buscarTenantConfig(UUID tenantId) {
        return migrator.withHandle(h -> h.createQuery("""
                        SELECT frete_fixo_centavos, formas_pagamento::text AS formas_pagamento,
                               minutos_reserva, dias_produto_parado
                        FROM tenant_config WHERE tenant_id = :tenantId
                        """)
                .bind("tenantId", tenantId)
                .map((rs, ctx) -> new LinhaTenantConfig(
                        rs.getInt("frete_fixo_centavos"),
                        rs.getString("formas_pagamento"),
                        rs.getInt("minutos_reserva"),
                        rs.getInt("dias_produto_parado")))
                .one());
    }

    // ---------------------------------------------------------------- caminho feliz

    @Test
    void criarLojaComSucessoCriaTenantTenantConfigEUsuarioAdminNoBanco() {
        UUID planoId = planoAtivo();
        String slug = slugUnico();
        String emailAdmin = emailUnico();

        Tenant tenant = tenantService.criarLoja(
                "Loja Sucesso", slug, planoId, "Admin da Loja", emailAdmin, SENHA_ADMIN);

        assertNotNull(tenant.id());
        assertEquals("Loja Sucesso", tenant.nome());
        assertEquals(slug, tenant.slug());
        assertEquals(StatusTenant.ATIVA, tenant.status());
        assertEquals(planoId, tenant.planoId());
        assertNotNull(tenant.criadoEm());

        // Confirma direto no banco -- nao so no retorno do metodo.
        assertEquals(1, contarTenantsComSlug(slug));

        LinhaTenantConfig config = buscarTenantConfig(tenant.id());
        assertEquals(0, config.freteFixoCentavos());
        assertEquals("[]", config.formasPagamento());
        assertEquals(30, config.minutosReserva());
        assertEquals(30, config.diasProdutoParado());

        Usuario admin = new UsuarioRepository(migrator).buscarPorEmail(emailAdmin)
                .orElseThrow(() -> new AssertionError("Usuario admin nao foi criado"));
        assertEquals(tenant.id(), admin.tenantId());
        assertEquals(Papel.ADMIN_LOJA, admin.papel());
        assertTrue(admin.ativo());
        assertNotEquals(SENHA_ADMIN, admin.senhaHash(), "senha nao pode ser gravada em texto puro");
        assertTrue(PasswordEncoder.confere(SENHA_ADMIN, admin.senhaHash()));
    }

    // ---------------------------------------------------------------- slug duplicado

    @Test
    void slugDuplicadoFalhaComSlugJaUsadoEPrimeiraLojaContinuaIntacta() {
        UUID planoId = planoAtivo();
        String slug = slugUnico();

        Tenant primeira = tenantService.criarLoja(
                "Loja Primeira", slug, planoId, "Admin Um", emailUnico(), SENHA_ADMIN);

        ApiException erro = assertThrows(ApiException.class, () -> tenantService.criarLoja(
                "Loja Segunda", slug, planoId, "Admin Dois", emailUnico(), SENHA_ADMIN));

        assertEquals(409, erro.status());
        assertEquals("SLUG_JA_USADO", erro.codigo());

        // A tentativa falha nao pode ter corrompido a primeira loja nem criado uma segunda linha.
        assertEquals(1, contarTenantsComSlug(slug));
        Tenant tenantAtual = new TenantRepository(appPlatform).listarTodos().stream()
                .filter(t -> t.id().equals(primeira.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Primeira loja desapareceu do banco"));
        assertEquals("Loja Primeira", tenantAtual.nome());
        assertEquals(StatusTenant.ATIVA, tenantAtual.status());
    }

    // ---------------------------------------------------------------- email duplicado

    @Test
    void emailDeAdminDuplicadoDeUmClienteExistenteFalhaComEmailJaCadastrado() {
        UUID planoId = planoAtivo();
        String emailCompartilhado = emailUnico();
        // Simula um email ja usado por um papel completamente diferente (cliente global,
        // sem tenant) -- a checagem de unicidade de email e sobre a tabela usuario inteira,
        // nao so sobre admins de loja.
        criarUsuarioComEmail(null, Papel.CLIENTE, emailCompartilhado);
        String slugNovo = slugUnico();

        ApiException erro = assertThrows(ApiException.class, () -> tenantService.criarLoja(
                "Loja Com Email Repetido", slugNovo, planoId, "Admin Repetido", emailCompartilhado, SENHA_ADMIN));

        assertEquals(409, erro.status());
        assertEquals("EMAIL_JA_CADASTRADO", erro.codigo());

        // Nenhuma linha parcial: nem o tenant, nem um segundo usuario com este email.
        assertEquals(0, contarTenantsComSlug(slugNovo));
        assertEquals(1, contarUsuariosComEmail(emailCompartilhado));
    }

    // ---------------------------------------------------------------- plano invalido

    @Test
    void planoInexistenteFalhaComPlanoInvalidoENadaECriado() {
        int tenantsAntes = contarTenants();
        String slug = slugUnico();
        String email = emailUnico();

        ApiException erro = assertThrows(ApiException.class, () -> tenantService.criarLoja(
                "Loja Fantasma", slug, UUID.randomUUID(), "Admin Fantasma", email, SENHA_ADMIN));

        assertEquals(400, erro.status());
        assertEquals("PLANO_INVALIDO", erro.codigo());

        assertEquals(tenantsAntes, contarTenants(), "Nenhum tenant orfao deveria ter sido criado");
        assertEquals(0, contarTenantsComSlug(slug));
        assertEquals(0, contarUsuariosComEmail(email));
    }

    @Test
    void planoInativoFalhaComPlanoInvalido() {
        UUID planoInativoId = planoInativo();
        int tenantsAntes = contarTenants();
        String slug = slugUnico();

        ApiException erro = assertThrows(ApiException.class, () -> tenantService.criarLoja(
                "Loja Com Plano Inativo", slug, planoInativoId, "Admin", emailUnico(), SENHA_ADMIN));

        assertEquals(400, erro.status());
        assertEquals("PLANO_INVALIDO", erro.codigo());
        assertEquals(tenantsAntes, contarTenants());
        assertEquals(0, contarTenantsComSlug(slug));
    }

    // ---------------------------------------------------------------- atomicidade

    @Test
    void falhaNoMeioDoFluxoNaoDeixaTenantOrfaoNemUsuarioParcial() {
        // Reusa o cenario de email duplicado (falha depois do plano ser validado, mas
        // antes/durante o insert) para confirmar que a contagem de linhas em tenant/usuario
        // nao muda: ou as 3 tabelas nascem juntas, ou nenhuma delas nasce.
        UUID planoId = planoAtivo();
        String emailCompartilhado = emailUnico();
        criarUsuarioComEmail(null, Papel.CLIENTE, emailCompartilhado);

        int tenantsAntes = contarTenants();
        String slug = slugUnico();

        assertThrows(ApiException.class, () -> tenantService.criarLoja(
                "Loja Atomicidade", slug, planoId, "Admin Atomicidade", emailCompartilhado, SENHA_ADMIN));

        assertEquals(tenantsAntes, contarTenants());
        assertEquals(0, contarTenantsComSlug(slug));
        assertEquals(1, contarUsuariosComEmail(emailCompartilhado));
    }

    // ---------------------------------------------------------------- corrida real (item 8)

    /**
     * Duas chamadas concorrentes de {@code criarLoja} com o MESMO slug: a checagem previa
     * ({@code existeSlug}) roda em READ COMMITTED, entao e possivel (e, com a barreira
     * abaixo, provavel) que as duas transacoes passem pela checagem antes de qualquer
     * commit. Nesse caso a colisao so aparece no INSERT (unique_violation, SQLSTATE 23505),
     * exercitando de fato {@code TenantService.traduzirViolacaoDeUnicidade}. Se a corrida
     * nao se materializar (uma transacao commitar antes da outra sequer chamar o metodo), a
     * segunda ainda cai no caminho de checagem previa -- o mesmo resultado observavel
     * (exatamente 1 sucesso, a outra com SLUG_JA_USADO, nunca uma excecao crua do driver).
     * Por isso a asserção central do teste (exatamente 1 sucesso / 1 erro traduzido) nao e
     * flaky mesmo que o interleaving exato varie entre execucoes.
     */
    @Test
    void duasCriacoesConcorrentesComMesmoSlugSoUmaTemSucesso() throws Exception {
        UUID planoId = planoAtivo();
        String slugDisputado = slugUnico();
        CyclicBarrier largada = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Tenant> futuroA = executor.submit(
                    tarefaCriarLoja(largada, "Loja Corrida A", slugDisputado, planoId, emailUnico()));
            Future<Tenant> futuroB = executor.submit(
                    tarefaCriarLoja(largada, "Loja Corrida B", slugDisputado, planoId, emailUnico()));

            Resultado resultadoA = coletar(futuroA);
            Resultado resultadoB = coletar(futuroB);

            long sucessos = List.of(resultadoA, resultadoB).stream().filter(r -> r.tenant != null).count();
            long falhasTraduzidas = List.of(resultadoA, resultadoB).stream()
                    .filter(r -> r.erro != null && "SLUG_JA_USADO".equals(r.erro.codigo()) && r.erro.status() == 409)
                    .count();

            assertEquals(1, sucessos, "Exatamente uma das duas chamadas concorrentes deveria criar a loja");
            assertEquals(1, falhasTraduzidas, "A chamada perdedora deveria receber SLUG_JA_USADO, nunca uma excecao crua");
            assertEquals(1, contarTenantsComSlug(slugDisputado), "So pode sobrar 1 tenant com o slug disputado");
        } finally {
            executor.shutdownNow();
        }
    }

    private record Resultado(Tenant tenant, ApiException erro) {
    }

    private static Callable<Tenant> tarefaCriarLoja(
            CyclicBarrier largada, String nome, String slug, UUID planoId, String emailAdmin) {
        return () -> {
            largada.await(10, TimeUnit.SECONDS);
            return tenantService.criarLoja(nome, slug, planoId, "Admin " + nome, emailAdmin, SENHA_ADMIN);
        };
    }

    private static Resultado coletar(Future<Tenant> futuro) throws InterruptedException {
        try {
            return new Resultado(futuro.get(10, TimeUnit.SECONDS), null);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ApiException apiException) {
                return new Resultado(null, apiException);
            }
            fail("Excecao inesperada (nao traduzida) na chamada concorrente: " + e.getCause());
            return null; // inalcancavel
        } catch (java.util.concurrent.TimeoutException e) {
            fail("Chamada concorrente nao terminou a tempo: " + e);
            return null; // inalcancavel
        }
    }
}

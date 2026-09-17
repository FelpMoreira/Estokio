package com.estokio.controller;

import com.estokio.PostgresTestBase;
import com.estokio.TestFixtures;
import com.estokio.config.JavalinConfig;
import com.estokio.domain.user.Papel;
import com.estokio.domain.user.Usuario;
import com.estokio.exception.GlobalExceptionHandler;
import com.estokio.repository.catalog.ProdutoRepository;
import com.estokio.repository.catalog.VariacaoRepository;
import com.estokio.repository.inventory.EstoqueRepository;
import com.estokio.security.JwtService;
import com.estokio.security.RoleMiddleware;
import com.estokio.security.TenantContext;
import com.estokio.security.TenantMiddleware;
import com.estokio.service.catalog.ProdutoService;
import com.estokio.service.inventory.EstoqueService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sub-entrega 1b da Fase 1 (ver Notas de Implementacao - Fase 1): RBAC e isolamento
 * multi-tenant de {@code /api/loja/produtos*} e {@code /api/loja/estoque*}, ponta a
 * ponta via HTTP real (token -> {@link TenantMiddleware} -> {@link RoleMiddleware} ->
 * controller -> service -> Postgres real), sem mock -- mesma disciplina de
 * {@link PlataformaRbacTest}. A suite de isolamento segue a mesma exigencia de
 * [[Multi-Tenancy e RLS]] do {@code RowLevelSecurityTest} da Fase 0: token do tenant B
 * em recurso do tenant A deve sempre resultar em {@code 404}, nunca {@code 403} nem
 * sucesso -- confirmar existencia de um recurso de outro tenant ja seria um vazamento.
 */
class LojaRbacTest extends PostgresTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String SENHA = "senha-qualquer-123";

    private static Javalin app;
    private static String baseUrl;
    private static JwtService jwtService;

    private static UUID tenantA;
    private static UUID tenantB;
    private static Usuario adminLojaA;
    private static Usuario operadorA;
    private static Usuario adminLojaB;
    private static Usuario superAdmin;
    private static Usuario cliente;

    @BeforeAll
    static void setUp() {
        tenantA = TestFixtures.criarTenant(migratorJdbi());
        tenantB = TestFixtures.criarTenant(migratorJdbi());

        adminLojaA = TestFixtures.criarUsuario(migratorJdbi(), tenantA, Papel.ADMIN_LOJA, SENHA, true);
        operadorA = TestFixtures.criarUsuario(migratorJdbi(), tenantA, Papel.OPERADOR, SENHA, true);
        adminLojaB = TestFixtures.criarUsuario(migratorJdbi(), tenantB, Papel.ADMIN_LOJA, SENHA, true);
        superAdmin = TestFixtures.criarUsuario(migratorJdbi(), null, Papel.SUPER_ADMIN, SENHA, true);
        cliente = TestFixtures.criarUsuario(migratorJdbi(), null, Papel.CLIENTE, SENHA, true);

        jwtService = new JwtService(
                "segredo-de-teste-com-pelo-menos-32-bytes-para-hs256!!",
                Duration.ofMinutes(15), Duration.ofDays(7));

        ProdutoRepository produtoRepository = new ProdutoRepository();
        VariacaoRepository variacaoRepository = new VariacaoRepository();
        EstoqueRepository estoqueRepository = new EstoqueRepository();
        ProdutoService produtoService = new ProdutoService(
                appUserJdbi(), produtoRepository, variacaoRepository, estoqueRepository);
        EstoqueService estoqueService = new EstoqueService(appUserJdbi(), estoqueRepository);
        ProdutoController produtoController = new ProdutoController(produtoService);
        EstoqueController estoqueController = new EstoqueController(estoqueService);

        app = JavalinConfig.criar();
        GlobalExceptionHandler.registrar(app);

        // Mesma montagem de Main para o prefixo /api/loja/*.
        TenantMiddleware tenantMiddleware = new TenantMiddleware(jwtService);
        RoleMiddleware roleMiddleware = new RoleMiddleware();
        app.before("/api/loja/*", tenantMiddleware);
        app.beforeMatched("/api/loja/*", roleMiddleware);
        app.after(ctx -> TenantContext.limpar());

        produtoController.registrar(app);
        estoqueController.registrar(app);
        app.start(0);
        baseUrl = "http://localhost:" + app.port();
    }

    @AfterAll
    static void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    // ---------------------------------------------------------------- infra HTTP

    private String tokenPara(Usuario usuario) {
        return jwtService.emitirAccessToken(usuario.id(), usuario.papel(), usuario.tenantId());
    }

    private HttpResponse<String> get(String caminho, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + caminho))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String caminho, String token, String corpoJson)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + caminho))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(corpoJson))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String caminho, String token, String corpoJson)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + caminho))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(corpoJson))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertAcessoNegado(HttpResponse<String> resposta) throws IOException {
        assertEquals(403, resposta.statusCode());
        JsonNode json = JSON.readTree(resposta.body());
        assertEquals("ACESSO_NEGADO", json.get("codigo").asText());
    }

    private void assertNaoEncontrado(HttpResponse<String> resposta) throws IOException {
        assertEquals(404, resposta.statusCode());
    }

    // ---------------------------------------------------------------- fixtures de dominio via HTTP

    private record ProdutoCriado(UUID id) {
    }

    private record VariacaoCriada(UUID id, String sku) {
    }

    private ProdutoCriado criarProdutoComo(Usuario usuario, String nome) throws Exception {
        HttpResponse<String> resposta = post("/api/loja/produtos", tokenPara(usuario),
                "{\"nome\": \"" + nome + "\"}");
        assertEquals(201, resposta.statusCode(), "falha ao preparar fixture: " + resposta.body());
        return new ProdutoCriado(UUID.fromString(JSON.readTree(resposta.body()).get("id").asText()));
    }

    private VariacaoCriada criarVariacaoComo(Usuario usuario, UUID produtoId, String sku) throws Exception {
        String corpo = "{\"sku\": \"" + sku + "\", \"preco_centavos\": 1000, \"ponto_reposicao\": 0}";
        HttpResponse<String> resposta = post(
                "/api/loja/produtos/" + produtoId + "/variacoes", tokenPara(usuario), corpo);
        assertEquals(201, resposta.statusCode(), "falha ao preparar fixture: " + resposta.body());
        return new VariacaoCriada(UUID.fromString(JSON.readTree(resposta.body()).get("id").asText()), sku);
    }

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------------------------------------------------------------- RBAC: GET /api/loja/produtos

    @Test
    void clienteRecebe403EmGetProdutos() throws Exception {
        assertAcessoNegado(get("/api/loja/produtos", tokenPara(cliente)));
    }

    @Test
    void superAdminRecebe403EmGetProdutos() throws Exception {
        // SUPER_ADMIN tem tenant_id nulo no token; se por algum bug essa rota nao checasse
        // o papel antes do tenant, o erro observado seria 403 (esta assercao) e nao um
        // NullPointerException/500 -- RoleMiddleware roda antes de qualquer acesso a tenant.
        assertAcessoNegado(get("/api/loja/produtos", tokenPara(superAdmin)));
    }

    @Test
    void adminLojaPassaEmGetProdutos() throws Exception {
        HttpResponse<String> resposta = get("/api/loja/produtos", tokenPara(adminLojaA));
        assertEquals(200, resposta.statusCode());
        assertTrue(JSON.readTree(resposta.body()).isArray());
    }

    @Test
    void operadorPassaEmGetProdutos() throws Exception {
        HttpResponse<String> resposta = get("/api/loja/produtos", tokenPara(operadorA));
        assertEquals(200, resposta.statusCode());
        assertTrue(JSON.readTree(resposta.body()).isArray());
    }

    // ---------------------------------------------------------------- RBAC: POST /api/loja/produtos

    @Test
    void clienteRecebe403EmPostProdutos() throws Exception {
        assertAcessoNegado(post("/api/loja/produtos", tokenPara(cliente), "{\"nome\": \"Produto Cliente\"}"));
    }

    @Test
    void superAdminRecebe403EmPostProdutos() throws Exception {
        assertAcessoNegado(post("/api/loja/produtos", tokenPara(superAdmin), "{\"nome\": \"Produto Super\"}"));
    }

    @Test
    void adminLojaPassaEmPostProdutosECria201() throws Exception {
        HttpResponse<String> resposta = post("/api/loja/produtos", tokenPara(adminLojaA), "{\"nome\": \"Produto Admin\"}");
        assertEquals(201, resposta.statusCode());
    }

    @Test
    void operadorPassaEmPostProdutosECria201() throws Exception {
        HttpResponse<String> resposta = post("/api/loja/produtos", tokenPara(operadorA), "{\"nome\": \"Produto Operador\"}");
        assertEquals(201, resposta.statusCode());
    }

    // ---------------------------------------------------------------- RBAC: PATCH /api/loja/variacoes/{id}

    @Test
    void clienteRecebe403EmPatchVariacoes() throws Exception {
        UUID idQualquer = UUID.randomUUID();
        assertAcessoNegado(patch("/api/loja/variacoes/" + idQualquer, tokenPara(cliente), "{\"preco_centavos\": 100}"));
    }

    @Test
    void superAdminRecebe403EmPatchVariacoes() throws Exception {
        UUID idQualquer = UUID.randomUUID();
        assertAcessoNegado(patch("/api/loja/variacoes/" + idQualquer, tokenPara(superAdmin), "{\"preco_centavos\": 100}"));
    }

    @Test
    void adminLojaPassaEmPatchVariacoes() throws Exception {
        ProdutoCriado produto = criarProdutoComo(adminLojaA, "Produto Patch RBAC " + sufixo());
        VariacaoCriada variacao = criarVariacaoComo(adminLojaA, produto.id(), "SKU-PATCH-" + sufixo());

        HttpResponse<String> resposta = patch(
                "/api/loja/variacoes/" + variacao.id(), tokenPara(adminLojaA), "{\"preco_centavos\": 2222}");

        assertEquals(200, resposta.statusCode());
    }

    // ---------------------------------------------------------------- RBAC: GET/POST /api/loja/estoque*

    @Test
    void clienteRecebe403EmGetEstoque() throws Exception {
        assertAcessoNegado(get("/api/loja/estoque", tokenPara(cliente)));
    }

    @Test
    void superAdminRecebe403EmGetEstoque() throws Exception {
        assertAcessoNegado(get("/api/loja/estoque", tokenPara(superAdmin)));
    }

    @Test
    void adminLojaPassaEmGetEstoque() throws Exception {
        HttpResponse<String> resposta = get("/api/loja/estoque", tokenPara(adminLojaA));
        assertEquals(200, resposta.statusCode());
        assertTrue(JSON.readTree(resposta.body()).isArray());
    }

    @Test
    void operadorPassaEmGetEstoque() throws Exception {
        HttpResponse<String> resposta = get("/api/loja/estoque", tokenPara(operadorA));
        assertEquals(200, resposta.statusCode());
        assertTrue(JSON.readTree(resposta.body()).isArray());
    }

    @Test
    void clienteRecebe403EmPostEstoqueEntrada() throws Exception {
        String corpo = "{\"variacao_id\": \"" + UUID.randomUUID() + "\", \"quantidade\": 1}";
        assertAcessoNegado(post("/api/loja/estoque/entrada", tokenPara(cliente), corpo));
    }

    @Test
    void superAdminRecebe403EmPostEstoqueEntrada() throws Exception {
        String corpo = "{\"variacao_id\": \"" + UUID.randomUUID() + "\", \"quantidade\": 1}";
        assertAcessoNegado(post("/api/loja/estoque/entrada", tokenPara(superAdmin), corpo));
    }

    @Test
    void adminLojaPassaEmPostEstoqueEntrada() throws Exception {
        ProdutoCriado produto = criarProdutoComo(adminLojaA, "Produto Entrada RBAC " + sufixo());
        VariacaoCriada variacao = criarVariacaoComo(adminLojaA, produto.id(), "SKU-ENTRADA-" + sufixo());

        String corpo = "{\"variacao_id\": \"" + variacao.id() + "\", \"quantidade\": 10, \"motivo\": \"teste rbac\"}";
        HttpResponse<String> resposta = post("/api/loja/estoque/entrada", tokenPara(adminLojaA), corpo);

        assertEquals(201, resposta.statusCode());
    }

    @Test
    void operadorPassaEmPostEstoqueEntrada() throws Exception {
        ProdutoCriado produto = criarProdutoComo(operadorA, "Produto Entrada Operador RBAC " + sufixo());
        VariacaoCriada variacao = criarVariacaoComo(operadorA, produto.id(), "SKU-ENTRADA-OP-" + sufixo());

        String corpo = "{\"variacao_id\": \"" + variacao.id() + "\", \"quantidade\": 5}";
        HttpResponse<String> resposta = post("/api/loja/estoque/entrada", tokenPara(operadorA), corpo);

        assertEquals(201, resposta.statusCode());
    }

    // ---------------------------------------------------------------- isolamento multi-tenant real via HTTP

    @Test
    void produtoDoTenantANaoAparecePorHttpParaOTenantB() throws Exception {
        String nomeUnico = "Produto Exclusivo De A " + sufixo();
        criarProdutoComo(adminLojaA, nomeUnico);

        HttpResponse<String> resposta = get("/api/loja/produtos", tokenPara(adminLojaB));

        assertEquals(200, resposta.statusCode());
        JsonNode lista = JSON.readTree(resposta.body());
        boolean vazou = false;
        for (JsonNode item : lista) {
            if (nomeUnico.equals(item.get("nome").asText())) {
                vazou = true;
            }
        }
        assertFalse(vazou, "produto do tenant A vazou na listagem do tenant B");
    }

    @Test
    void saldoDoTenantANaoAparecePorHttpParaOTenantB() throws Exception {
        ProdutoCriado produto = criarProdutoComo(adminLojaA, "Produto Saldo Isolado " + sufixo());
        String skuUnico = "SKU-ISOLADO-" + sufixo();
        criarVariacaoComo(adminLojaA, produto.id(), skuUnico);

        HttpResponse<String> resposta = get("/api/loja/estoque", tokenPara(adminLojaB));

        assertEquals(200, resposta.statusCode());
        JsonNode lista = JSON.readTree(resposta.body());
        boolean vazou = false;
        for (JsonNode item : lista) {
            if (skuUnico.equals(item.get("sku").asText())) {
                vazou = true;
            }
        }
        assertFalse(vazou, "saldo de estoque do tenant A vazou na listagem do tenant B");
    }

    @Test
    void patchDeVariacaoDeOutroTenantRetorna404PorHttpNaoAutorizadoNemSucesso() throws Exception {
        ProdutoCriado produto = criarProdutoComo(adminLojaA, "Produto Alvo Patch " + sufixo());
        VariacaoCriada variacao = criarVariacaoComo(adminLojaA, produto.id(), "SKU-ALVO-PATCH-" + sufixo());

        HttpResponse<String> resposta = patch(
                "/api/loja/variacoes/" + variacao.id(), tokenPara(adminLojaB), "{\"preco_centavos\": 1}");

        assertNaoEncontrado(resposta);
    }

    @Test
    void entradaDeEstoqueEmVariacaoDeOutroTenantRetorna404PorHttp() throws Exception {
        ProdutoCriado produto = criarProdutoComo(adminLojaA, "Produto Alvo Entrada " + sufixo());
        VariacaoCriada variacao = criarVariacaoComo(adminLojaA, produto.id(), "SKU-ALVO-ENTRADA-" + sufixo());

        String corpo = "{\"variacao_id\": \"" + variacao.id() + "\", \"quantidade\": 99}";
        HttpResponse<String> resposta = post("/api/loja/estoque/entrada", tokenPara(adminLojaB), corpo);

        assertNaoEncontrado(resposta);
    }
}

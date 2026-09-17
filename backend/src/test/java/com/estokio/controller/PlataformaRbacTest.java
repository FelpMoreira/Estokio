package com.estokio.controller;

import com.estokio.PostgresTestBase;
import com.estokio.TestFixtures;
import com.estokio.config.JavalinConfig;
import com.estokio.domain.user.Papel;
import com.estokio.domain.user.Usuario;
import com.estokio.exception.GlobalExceptionHandler;
import com.estokio.repository.auth.UsuarioRepository;
import com.estokio.repository.tenant.PlanoRepository;
import com.estokio.repository.tenant.TenantRepository;
import com.estokio.security.JwtService;
import com.estokio.security.RoleMiddleware;
import com.estokio.security.TenantContext;
import com.estokio.security.TenantMiddleware;
import com.estokio.service.tenant.TenantService;
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

/**
 * RBAC de {@code /api/plataforma/**} (Fase 1, item 1): so {@link Papel#SUPER_ADMIN}
 * pode passar. Sobe o mesmo par {@link TenantMiddleware}/{@link RoleMiddleware} + o
 * Javalin real usado por {@code Main} (mesma montagem que {@code ApiErrorContractTest}
 * usa para {@code /api/auth/**}), contra Postgres real, sem mock nenhum na cadeia
 * token -> middleware -> controller -> service -> banco.
 */
class PlataformaRbacTest extends PostgresTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String SENHA = "senha-qualquer-123";

    private static Javalin app;
    private static String baseUrl;
    private static JwtService jwtService;

    private static UUID tenantId;
    private static UUID planoId;
    private static Usuario superAdmin;
    private static Usuario adminLoja;
    private static Usuario operador;
    private static Usuario cliente;

    @BeforeAll
    static void setUp() {
        tenantId = TestFixtures.criarTenant(migratorJdbi());
        planoId = TestFixtures.criarPlano(migratorJdbi(), "Plano RBAC " + UUID.randomUUID().toString().substring(0, 8));

        superAdmin = TestFixtures.criarUsuario(migratorJdbi(), null, Papel.SUPER_ADMIN, SENHA, true);
        adminLoja = TestFixtures.criarUsuario(migratorJdbi(), tenantId, Papel.ADMIN_LOJA, SENHA, true);
        operador = TestFixtures.criarUsuario(migratorJdbi(), tenantId, Papel.OPERADOR, SENHA, true);
        cliente = TestFixtures.criarUsuario(migratorJdbi(), null, Papel.CLIENTE, SENHA, true);

        jwtService = new JwtService(
                "segredo-de-teste-com-pelo-menos-32-bytes-para-hs256!!",
                Duration.ofMinutes(15), Duration.ofDays(7));

        TenantRepository tenantRepository = new TenantRepository(appPlatformJdbi());
        PlanoRepository planoRepository = new PlanoRepository(appPlatformJdbi());
        UsuarioRepository usuarioRepositoryPlataforma = new UsuarioRepository(appPlatformJdbi());
        TenantService tenantService = new TenantService(
                appPlatformJdbi(), tenantRepository, planoRepository, usuarioRepositoryPlataforma);
        PlataformaController plataformaController = new PlataformaController(tenantService);

        app = JavalinConfig.criar();
        GlobalExceptionHandler.registrar(app);

        // Mesma montagem de Main para o prefixo /api/plataforma/*.
        TenantMiddleware tenantMiddleware = new TenantMiddleware(jwtService);
        RoleMiddleware roleMiddleware = new RoleMiddleware();
        app.before("/api/plataforma/*", tenantMiddleware);
        app.beforeMatched("/api/plataforma/*", roleMiddleware);
        app.after(ctx -> TenantContext.limpar());

        plataformaController.registrar(app);
        app.start(0);
        baseUrl = "http://localhost:" + app.port();
    }

    @AfterAll
    static void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    private String tokenPara(Usuario usuario) {
        return jwtService.emitirAccessToken(usuario.id(), usuario.papel(), usuario.tenantId());
    }

    private HttpResponse<String> get(String caminho, String tokenOuNulo) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + caminho))
                .GET();
        if (tokenOuNulo != null) {
            builder.header("Authorization", "Bearer " + tokenOuNulo);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String caminho, String tokenOuNulo, String corpoJson)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + caminho))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpoJson));
        if (tokenOuNulo != null) {
            builder.header("Authorization", "Bearer " + tokenOuNulo);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ---------------------------------------------------------------- sem autenticacao

    @Test
    void semTokenRetorna401EmVezDe403NasRotasDePlataforma() throws Exception {
        HttpResponse<String> resposta = get("/api/plataforma/lojas", null);

        assertEquals(401, resposta.statusCode());
        JsonNode json = JSON.readTree(resposta.body());
        assertEquals("NAO_AUTORIZADO", json.get("codigo").asText());
    }

    // ---------------------------------------------------------------- GET /api/plataforma/lojas

    @Test
    void adminLojaRecebe403EmGetLojas() throws Exception {
        assertAcessoNegado(get("/api/plataforma/lojas", tokenPara(adminLoja)));
    }

    @Test
    void operadorRecebe403EmGetLojas() throws Exception {
        assertAcessoNegado(get("/api/plataforma/lojas", tokenPara(operador)));
    }

    @Test
    void clienteRecebe403EmGetLojas() throws Exception {
        assertAcessoNegado(get("/api/plataforma/lojas", tokenPara(cliente)));
    }

    @Test
    void superAdminPassaEmGetLojas() throws Exception {
        HttpResponse<String> resposta = get("/api/plataforma/lojas", tokenPara(superAdmin));

        assertEquals(200, resposta.statusCode());
        assertJsonArray(resposta.body());
    }

    // ---------------------------------------------------------------- GET /api/plataforma/planos

    @Test
    void adminLojaRecebe403EmGetPlanos() throws Exception {
        assertAcessoNegado(get("/api/plataforma/planos", tokenPara(adminLoja)));
    }

    @Test
    void operadorRecebe403EmGetPlanos() throws Exception {
        assertAcessoNegado(get("/api/plataforma/planos", tokenPara(operador)));
    }

    @Test
    void clienteRecebe403EmGetPlanos() throws Exception {
        assertAcessoNegado(get("/api/plataforma/planos", tokenPara(cliente)));
    }

    @Test
    void superAdminPassaEmGetPlanos() throws Exception {
        HttpResponse<String> resposta = get("/api/plataforma/planos", tokenPara(superAdmin));

        assertEquals(200, resposta.statusCode());
        assertJsonArray(resposta.body());
    }

    // ---------------------------------------------------------------- POST /api/plataforma/lojas

    private String corpoCriarLoja(String slug, String email) {
        return """
                {"loja": {"nome": "Loja RBAC", "slug": "%s", "plano_id": "%s"},
                 "admin": {"nome": "Admin RBAC", "email": "%s", "senha": "senha12345"}}
                """.formatted(slug, planoId, email);
    }

    @Test
    void adminLojaRecebe403EmPostLojas() throws Exception {
        String corpo = corpoCriarLoja("loja-rbac-admin", "admin-rbac-1@estokio-test.dev");
        assertAcessoNegado(post("/api/plataforma/lojas", tokenPara(adminLoja), corpo));
    }

    @Test
    void operadorRecebe403EmPostLojas() throws Exception {
        String corpo = corpoCriarLoja("loja-rbac-operador", "admin-rbac-2@estokio-test.dev");
        assertAcessoNegado(post("/api/plataforma/lojas", tokenPara(operador), corpo));
    }

    @Test
    void clienteRecebe403EmPostLojas() throws Exception {
        String corpo = corpoCriarLoja("loja-rbac-cliente", "admin-rbac-3@estokio-test.dev");
        assertAcessoNegado(post("/api/plataforma/lojas", tokenPara(cliente), corpo));
    }

    @Test
    void superAdminPassaEmPostLojasECria201() throws Exception {
        String corpo = corpoCriarLoja("loja-rbac-super-admin", "admin-rbac-super@estokio-test.dev");

        HttpResponse<String> resposta = post("/api/plataforma/lojas", tokenPara(superAdmin), corpo);

        assertEquals(201, resposta.statusCode());
        JsonNode json = JSON.readTree(resposta.body());
        assertEquals("loja-rbac-super-admin", json.get("slug").asText());
    }

    // ---------------------------------------------------------------- helpers de asserção

    private void assertAcessoNegado(HttpResponse<String> resposta) throws IOException {
        assertEquals(403, resposta.statusCode());
        JsonNode json = JSON.readTree(resposta.body());
        assertEquals("ACESSO_NEGADO", json.get("codigo").asText());
    }

    private void assertJsonArray(String corpo) throws IOException {
        JsonNode json = JSON.readTree(corpo);
        org.junit.jupiter.api.Assertions.assertTrue(json.isArray());
    }
}

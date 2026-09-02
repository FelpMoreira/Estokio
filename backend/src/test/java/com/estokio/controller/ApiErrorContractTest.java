package com.estokio.controller;

import com.estokio.PostgresTestBase;
import com.estokio.TestFixtures;
import com.estokio.config.JavalinConfig;
import com.estokio.domain.user.Papel;
import com.estokio.domain.user.Usuario;
import com.estokio.exception.GlobalExceptionHandler;
import com.estokio.repository.auth.RefreshTokenRepository;
import com.estokio.repository.auth.UsuarioRepository;
import com.estokio.security.JwtService;
import com.estokio.service.auth.AuthService;
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
 * Contrato de erro da secao 11 da spec ({@code {codigo, mensagem, detalhes}}), testado
 * ponta a ponta via HTTP real -- sobe o Javalin de verdade (mesma montagem de
 * {@link com.estokio.Main}, so numa porta efemera) contra o Postgres de teste, sem
 * mock nenhum na cadeia controller -> service -> repository -> banco.
 */
class ApiErrorContractTest extends PostgresTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static Javalin app;
    private static String baseUrl;
    private static Usuario usuario;
    private static final String SENHA_CORRETA = "senha-correta-123";

    @BeforeAll
    static void setUp() {
        UUID tenantId = TestFixtures.criarTenant(migratorJdbi());
        usuario = TestFixtures.criarUsuario(migratorJdbi(), tenantId, Papel.ADMIN_LOJA, SENHA_CORRETA, true);

        JwtService jwtService = new JwtService(
                "segredo-de-teste-com-pelo-menos-32-bytes-para-hs256!!",
                Duration.ofMinutes(15), Duration.ofDays(7));
        UsuarioRepository usuarioRepository = new UsuarioRepository(appUserJdbi());
        RefreshTokenRepository refreshTokenRepository = new RefreshTokenRepository(appUserJdbi());
        AuthService authService = new AuthService(usuarioRepository, refreshTokenRepository, jwtService);
        AuthController authController = new AuthController(authService);

        app = JavalinConfig.criar();
        GlobalExceptionHandler.registrar(app);
        authController.registrar(app);
        app.start(0);
        baseUrl = "http://localhost:" + app.port();
    }

    @AfterAll
    static void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    private HttpResponse<String> post(String caminho, String corpoJson) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + caminho))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpoJson))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void loginComSenhaErradaRetornaContratoDeErroPadrao() throws Exception {
        String corpo = """
                {"email": "%s", "senha": "senha-errada"}""".formatted(usuario.email());

        HttpResponse<String> resposta = post("/api/auth/login", corpo);

        assertEquals(401, resposta.statusCode());
        JsonNode json = JSON.readTree(resposta.body());
        assertEquals("NAO_AUTORIZADO", json.get("codigo").asText());
        assertTrue(json.has("mensagem") && !json.get("mensagem").asText().isBlank());
        assertTrue(json.get("detalhes").isArray());
        assertEquals(0, json.get("detalhes").size());
    }

    @Test
    void loginComCorpoInvalidoRetornaContratoDeErroDeValidacao() throws Exception {
        // email ausente -- viola a validacao declarada em AuthController#login.
        String corpo = """
                {"senha": "qualquer-coisa"}""";

        HttpResponse<String> resposta = post("/api/auth/login", corpo);

        assertEquals(400, resposta.statusCode());
        JsonNode json = JSON.readTree(resposta.body());
        assertEquals("VALIDACAO", json.get("codigo").asText());
        assertTrue(json.get("detalhes").isArray());
        assertTrue(json.get("detalhes").size() > 0, "Esperava pelo menos 1 detalhe de campo invalido");
        assertTrue(json.get("detalhes").get(0).has("campo"));
        assertTrue(json.get("detalhes").get(0).has("mensagens"));
    }

    @Test
    void refreshComTokenAusenteRetornaContratoDeErroDeValidacao() throws Exception {
        HttpResponse<String> resposta = post("/api/auth/refresh", "{}");

        assertEquals(400, resposta.statusCode());
        JsonNode json = JSON.readTree(resposta.body());
        assertEquals("VALIDACAO", json.get("codigo").asText());
    }

    @Test
    void loginComSucessoRetorna200ETokensSemAConcatenacaoDeErro() throws Exception {
        // Caso feliz, para garantir que o contrato de ERRO nao vaza para a resposta de
        // sucesso (nenhum campo "codigo"/"detalhes" numa resposta 200).
        String corpo = """
                {"email": "%s", "senha": "%s"}""".formatted(usuario.email(), SENHA_CORRETA);

        HttpResponse<String> resposta = post("/api/auth/login", corpo);

        assertEquals(200, resposta.statusCode());
        JsonNode json = JSON.readTree(resposta.body());
        assertTrue(json.has("access_token") && !json.get("access_token").asText().isBlank());
        assertTrue(json.has("refresh_token") && !json.get("refresh_token").asText().isBlank());
        assertFalse(json.has("codigo"));
    }
}

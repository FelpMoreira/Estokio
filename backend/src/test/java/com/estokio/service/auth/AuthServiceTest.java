package com.estokio.service.auth;

import com.estokio.PostgresTestBase;
import com.estokio.TestFixtures;
import com.estokio.domain.user.Papel;
import com.estokio.domain.user.RefreshToken;
import com.estokio.domain.user.Usuario;
import com.estokio.exception.ApiException;
import com.estokio.repository.auth.RefreshTokenRepository;
import com.estokio.repository.auth.UsuarioRepository;
import com.estokio.security.JwtService;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As duas unicas regras de negocio da Fase 0 (ver {@link com.estokio.controller.AuthController}),
 * testadas na camada de servico, direto contra Postgres real (UsuarioRepository e
 * RefreshTokenRepository reais, nada mockado). {@link JwtService} usa um segredo de
 * teste fixo, independente do ambiente.
 */
class AuthServiceTest extends PostgresTestBase {

    private static final String SENHA_CORRETA = "senha-correta-123";

    private static UUID tenantId;
    private static AuthService authService;
    private static JwtService jwtService;
    private static UsuarioRepository usuarioRepository;
    private static RefreshTokenRepository refreshTokenRepository;

    @BeforeAll
    static void setUp() {
        Jdbi migrator = migratorJdbi();
        Jdbi appUser = appUserJdbi();
        tenantId = TestFixtures.criarTenant(migrator);

        jwtService = new JwtService(
                "segredo-de-teste-com-pelo-menos-32-bytes-para-hs256!!",
                Duration.ofMinutes(15), Duration.ofDays(7));
        usuarioRepository = new UsuarioRepository(appUser);
        refreshTokenRepository = new RefreshTokenRepository(appUser);
        authService = new AuthService(usuarioRepository, refreshTokenRepository, jwtService);
    }

    private Usuario criarUsuarioComSenha(Papel papel, boolean ativo) {
        UUID tenantDoUsuario = papel == Papel.ADMIN_LOJA || papel == Papel.OPERADOR ? tenantId : null;
        return TestFixtures.criarUsuario(migratorJdbi(), tenantDoUsuario, papel, SENHA_CORRETA, ativo);
    }

    // ---------------------------------------------------------------- login

    @Test
    void loginComCredenciaisCorretasEmiteParDeTokensValido() {
        Usuario usuario = criarUsuarioComSenha(Papel.ADMIN_LOJA, true);

        AuthService.TokenPair par = authService.login(usuario.email(), SENHA_CORRETA);

        assertNotNull(par.accessToken());
        assertNotNull(par.refreshToken());
        assertTrue(!par.accessToken().isBlank() && !par.refreshToken().isBlank());

        JwtService.AccessTokenClaims claims = jwtService.validarAccessToken(par.accessToken());
        assertEquals(usuario.id(), claims.usuarioId());
        assertEquals(usuario.papel(), claims.papel());
        assertEquals(tenantId, claims.tenantId());
    }

    @Test
    void loginComSenhaErradaLancaNaoAutorizado() {
        Usuario usuario = criarUsuarioComSenha(Papel.ADMIN_LOJA, true);

        ApiException erro = assertThrows(ApiException.class,
                () -> authService.login(usuario.email(), "senha-errada"));

        assertEquals(401, erro.status());
        assertEquals("NAO_AUTORIZADO", erro.codigo());
    }

    @Test
    void loginComEmailInexistenteLancaMesmoErroQueSenhaErrada_semEnumeracaoDeUsuario() {
        ApiException erroEmailInexistente = assertThrows(ApiException.class,
                () -> authService.login("nao-existe-" + UUID.randomUUID() + "@estokio-test.dev", "qualquer-senha"));

        Usuario usuario = criarUsuarioComSenha(Papel.ADMIN_LOJA, true);
        ApiException erroSenhaErrada = assertThrows(ApiException.class,
                () -> authService.login(usuario.email(), "senha-errada"));

        // A prova de que nao ha enumeracao de usuario: os dois ramos (email nao existe /
        // email existe mas senha errada) precisam ser INDISTINGUIVEIS para quem chama --
        // mesmo status, mesmo codigo, mesma mensagem.
        assertEquals(erroSenhaErrada.status(), erroEmailInexistente.status());
        assertEquals(erroSenhaErrada.codigo(), erroEmailInexistente.codigo());
        assertEquals(erroSenhaErrada.getMessage(), erroEmailInexistente.getMessage());
    }

    @Test
    void loginComUsuarioInativoLancaNaoAutorizado() {
        Usuario usuario = criarUsuarioComSenha(Papel.ADMIN_LOJA, false);

        ApiException erro = assertThrows(ApiException.class,
                () -> authService.login(usuario.email(), SENHA_CORRETA));

        assertEquals(401, erro.status());
        assertEquals("NAO_AUTORIZADO", erro.codigo());
    }

    // ---------------------------------------------------------------- refresh

    @Test
    void refreshValidoEmiteNovoParERevogaOAntigo() {
        Usuario usuario = criarUsuarioComSenha(Papel.OPERADOR, true);
        AuthService.TokenPair parOriginal = authService.login(usuario.email(), SENHA_CORRETA);

        AuthService.TokenPair parNovo = authService.refresh(parOriginal.refreshToken());

        // Nao comparamos accessToken novo vs antigo por igualdade: se login() e refresh()
        // rodarem no mesmo segundo, o JWT (sub/papel/tenant_id/iat/exp identicos) sai
        // byte-a-byte igual, e isso e esperado para um token stateless -- nao e um reuso
        // indevido. O que precisa mudar de fato, sempre, e o refresh token (alta entropia
        // aleatoria por emissao, Decisao de kickoff #11).
        assertNotEquals(parOriginal.refreshToken(), parNovo.refreshToken());
        JwtService.AccessTokenClaims claimsNovo = jwtService.validarAccessToken(parNovo.accessToken());
        assertEquals(usuario.id(), claimsNovo.usuarioId());
        assertEquals(usuario.papel(), claimsNovo.papel());

        String hashAntigo = jwtService.hashRefreshToken(parOriginal.refreshToken());
        Optional<RefreshToken> tokenAntigo = refreshTokenRepository.buscarPorHash(hashAntigo);
        assertTrue(tokenAntigo.isPresent());
        assertNotNull(tokenAntigo.get().revogadoEm(), "Refresh token antigo deveria estar revogado apos o refresh");

        String hashNovo = jwtService.hashRefreshToken(parNovo.refreshToken());
        Optional<RefreshToken> tokenNovo = refreshTokenRepository.buscarPorHash(hashNovo);
        assertTrue(tokenNovo.isPresent());
        assertTrue(tokenNovo.get().valido(Instant.now()));
    }

    @Test
    void reusarRefreshTokenJaConsumidoFalha() {
        Usuario usuario = criarUsuarioComSenha(Papel.OPERADOR, true);
        AuthService.TokenPair par = authService.login(usuario.email(), SENHA_CORRETA);

        authService.refresh(par.refreshToken()); // primeiro uso: consome o token original

        ApiException erro = assertThrows(ApiException.class, () -> authService.refresh(par.refreshToken()));
        assertEquals(401, erro.status());
        assertEquals("NAO_AUTORIZADO", erro.codigo());
    }

    @Test
    void refreshComTokenInexistenteLancaNaoAutorizado() {
        ApiException erro = assertThrows(ApiException.class,
                () -> authService.refresh("token-que-nunca-foi-emitido-" + UUID.randomUUID()));

        assertEquals(401, erro.status());
        assertEquals("NAO_AUTORIZADO", erro.codigo());
    }

    @Test
    void refreshComTokenExpiradoLancaNaoAutorizado() {
        Usuario usuario = criarUsuarioComSenha(Papel.OPERADOR, true);
        String tokenBruto = jwtService.gerarRefreshTokenBruto();
        String hash = jwtService.hashRefreshToken(tokenBruto);
        // Insere diretamente com expira_em no passado -- simula um token emitido ha muito
        // tempo, sem esperar o TTL real de dias passar.
        refreshTokenRepository.salvar(usuario.id(), hash, Instant.now().minusSeconds(10));

        ApiException erro = assertThrows(ApiException.class, () -> authService.refresh(tokenBruto));
        assertEquals(401, erro.status());
        assertEquals("NAO_AUTORIZADO", erro.codigo());
    }
}

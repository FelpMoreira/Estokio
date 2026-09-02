package com.estokio;

import com.estokio.config.DatabaseConfig;
import com.estokio.config.JavalinConfig;
import com.estokio.controller.AuthController;
import com.estokio.exception.GlobalExceptionHandler;
import com.estokio.repository.auth.RefreshTokenRepository;
import com.estokio.repository.auth.UsuarioRepository;
import com.estokio.security.JwtService;
import com.estokio.security.RoleMiddleware;
import com.estokio.security.TenantContext;
import com.estokio.security.TenantMiddleware;
import com.estokio.service.auth.AuthService;
import io.javalin.Javalin;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Bootstrap da aplicacao: roda as migrations Flyway com o papel {@code migrator},
 * monta as dependencias (repositorio -> service -> controller) e sobe o Javalin.
 * Ver Fase 0 - Fundacao no vault para o escopo desta fase.
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /** Rotas que exigem access token; nunca inclui /api/auth/**, que e publica por natureza. */
    private static final String[] PREFIXOS_PROTEGIDOS = {
            "/api/loja/*", "/api/plataforma/*", "/api/pedidos*", "/api/meus-pedidos/*"
    };

    public static void main(String[] args) {
        log.info("Rodando migrations Flyway (papel migrator)...");
        DatabaseConfig.rodarMigrations();

        Jdbi appUserJdbi = DatabaseConfig.appUserJdbi();

        JwtService jwtService = new JwtService(
                requiredEnv("ESTOKIO_JWT_SECRET"),
                Duration.ofMinutes(Long.parseLong(env("ESTOKIO_JWT_ACCESS_TTL_MINUTES", "15"))),
                Duration.ofDays(Long.parseLong(env("ESTOKIO_JWT_REFRESH_TTL_DAYS", "7"))));

        UsuarioRepository usuarioRepository = new UsuarioRepository(appUserJdbi);
        RefreshTokenRepository refreshTokenRepository = new RefreshTokenRepository(appUserJdbi);
        AuthService authService = new AuthService(usuarioRepository, refreshTokenRepository, jwtService);
        AuthController authController = new AuthController(authService);

        Javalin app = JavalinConfig.criar();
        GlobalExceptionHandler.registrar(app);

        TenantMiddleware tenantMiddleware = new TenantMiddleware(jwtService);
        RoleMiddleware roleMiddleware = new RoleMiddleware();
        for (String prefixo : PREFIXOS_PROTEGIDOS) {
            app.before(prefixo, tenantMiddleware);
            app.beforeMatched(prefixo, roleMiddleware);
        }
        app.after(ctx -> TenantContext.limpar());

        authController.registrar(app);

        int porta = Integer.parseInt(env("ESTOKIO_HTTP_PORT", "7000"));
        app.start(porta);
        log.info("Estokio backend no ar na porta {}", porta);
    }

    private static String env(String chave, String padrao) {
        String valor = System.getenv(chave);
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }

    private static String requiredEnv(String chave) {
        String valor = System.getenv(chave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variavel de ambiente obrigatoria ausente: " + chave);
        }
        return valor;
    }
}

package com.estokio.controller;

import com.estokio.service.auth.AuthService;
import io.javalin.Javalin;
import io.javalin.http.Context;

/**
 * As duas unicas rotas de negocio da Fase 0 (ver Fase 0 - Fundacao no vault):
 * provam JWT + BCrypt + contrato de erro funcionando ponta a ponta. Publicas --
 * nao passam por {@link com.estokio.security.TenantMiddleware}.
 */
public final class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public void registrar(Javalin app) {
        app.post("/api/auth/login", this::login);
        app.post("/api/auth/refresh", this::refresh);
    }

    private void login(Context ctx) {
        LoginRequest corpo = ctx.bodyValidator(LoginRequest.class)
                .check(r -> r.email() != null && !r.email().isBlank(), "email e obrigatorio")
                .check(r -> r.senha() != null && !r.senha().isBlank(), "senha e obrigatoria")
                .get();

        AuthService.TokenPair par = authService.login(corpo.email().trim().toLowerCase(), corpo.senha());
        ctx.status(200).json(new TokenResponse(par.accessToken(), par.refreshToken()));
    }

    private void refresh(Context ctx) {
        RefreshRequest corpo = ctx.bodyValidator(RefreshRequest.class)
                .check(r -> r.refreshToken() != null && !r.refreshToken().isBlank(), "refresh_token e obrigatorio")
                .get();

        AuthService.TokenPair par = authService.refresh(corpo.refreshToken());
        ctx.status(200).json(new TokenResponse(par.accessToken(), par.refreshToken()));
    }

    private record LoginRequest(String email, String senha) {
    }

    private record RefreshRequest(String refreshToken) {
    }

    private record TokenResponse(String accessToken, String refreshToken) {
    }
}

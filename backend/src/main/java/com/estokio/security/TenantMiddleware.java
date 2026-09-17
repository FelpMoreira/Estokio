package com.estokio.security;

import com.estokio.exception.ApiException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;

/**
 * Extrai o usuario autenticado do header {@code Authorization: Bearer <access_token>}
 * e popula o {@link TenantContext} para a requisicao atual (camada 2 da defesa em
 * profundidade de Multi-Tenancy e RLS). Registrar via {@code app.before(path, ...)}
 * apenas nas rotas que exigem autenticacao -- nunca em {@code /api/auth/**}.
 */
public final class TenantMiddleware implements Handler {

    private final JwtService jwtService;

    public TenantMiddleware(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void handle(Context ctx) {
        // Preflight de CORS: o navegador nunca manda Authorization num OPTIONS (ver spec
        // Fetch/CORS), entao exigir o header aqui derrubaria o preflight com 401 antes do
        // plugin de CORS do Javalin conseguir responder com os headers Access-Control-*
        // (so ficou visivel na Fase 1: e a primeira rota protegida que o frontend de fato
        // chama com um header custom, cruzando origem 5173 -> 7000).
        if (ctx.method() == HandlerType.OPTIONS) {
            return;
        }

        String cabecalho = ctx.header("Authorization");
        if (cabecalho == null || !cabecalho.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw ApiException.naoAutorizado("Header Authorization ausente ou mal formatado.");
        }
        String token = cabecalho.substring(7).trim();
        JwtService.AccessTokenClaims claims = jwtService.validarAccessToken(token);
        TenantContext.definir(claims.usuarioId(), claims.papel(), claims.tenantId());
    }
}

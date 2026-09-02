package com.estokio.security;

import com.estokio.exception.ApiException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.security.RouteRole;

import java.util.Set;

/**
 * RBAC declarativo por rota (matriz de permissoes -- ver Papeis e Permissoes). Roda
 * depois do {@link TenantMiddleware}, registrado como {@code app.beforeMatched(...)}
 * (precisa do endpoint ja resolvido para {@link Context#routeRoles()} existir). Rotas
 * sem papel declarado ficam liberadas para qualquer usuario autenticado.
 */
public final class RoleMiddleware implements Handler {

    @Override
    public void handle(Context ctx) {
        Set<RouteRole> papeisPermitidos = ctx.routeRoles();
        if (papeisPermitidos.isEmpty()) {
            return;
        }
        PapelRole papelAtual = PapelRole.de(TenantContext.papel());
        if (!papeisPermitidos.contains(papelAtual)) {
            throw ApiException.acessoNegado("Papel " + papelAtual + " nao tem acesso a este recurso.");
        }
    }
}

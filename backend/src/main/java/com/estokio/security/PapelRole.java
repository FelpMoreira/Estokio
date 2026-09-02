package com.estokio.security;

import com.estokio.domain.user.Papel;
import io.javalin.security.RouteRole;

/**
 * Adapta {@link Papel} ao marker interface {@link RouteRole} exigido pelo Javalin para
 * declarar RBAC por rota (ex: {@code app.post(path, handler, PapelRole.ADMIN_LOJA)}).
 * Existe para nao acoplar o enum de dominio a um tipo do framework web.
 */
public enum PapelRole implements RouteRole {
    SUPER_ADMIN,
    ADMIN_LOJA,
    OPERADOR,
    CLIENTE;

    public static PapelRole de(Papel papel) {
        return PapelRole.valueOf(papel.name());
    }
}

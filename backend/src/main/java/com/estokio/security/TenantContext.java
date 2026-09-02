package com.estokio.security;

import com.estokio.domain.user.Papel;
import com.estokio.exception.ApiException;
import org.jdbi.v3.core.Handle;

import java.util.UUID;

/**
 * Identidade da requisicao atual, populada pelo {@link TenantMiddleware} a partir do
 * access token. ThreadLocal: Jetty/Javalin processa cada requisicao HTTP numa unica
 * thread do pool, entao o valor e valido do inicio ao fim de uma requisicao -- desde
 * que {@link #limpar()} seja sempre chamado ao final (registrado em Main como
 * {@code app.after(...)}), para nao vazar para a proxima requisicao que reuse a thread.
 */
public final class TenantContext {

    private static final ThreadLocal<Dados> ATUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void definir(UUID usuarioId, Papel papel, UUID tenantId) {
        ATUAL.set(new Dados(usuarioId, papel, tenantId));
    }

    public static void limpar() {
        ATUAL.remove();
    }

    public static UUID usuarioId() {
        return exigirDados().usuarioId();
    }

    public static Papel papel() {
        return exigirDados().papel();
    }

    /** Pode ser null: SUPER_ADMIN e CLIENTE nao pertencem a um tenant (ver Papeis e Permissoes). */
    public static UUID tenantIdOuNulo() {
        return exigirDados().tenantId();
    }

    /** Usar em rotas {@code /api/loja/**}, onde tenant_id e obrigatorio no token. */
    public static UUID tenantIdObrigatorio() {
        UUID tenantId = tenantIdOuNulo();
        if (tenantId == null) {
            throw ApiException.acessoNegado("Esta rota exige um usuario associado a uma loja.");
        }
        return tenantId;
    }

    /**
     * Equivalente a {@code SET LOCAL app.tenant_id = ?}, mas via {@code set_config}
     * (que aceita bind parameter, ao contrario de SET LOCAL) -- ver Multi-Tenancy e RLS.
     * Deve ser a primeira instrucao de toda transacao Jdbi que toca uma tabela com RLS.
     */
    public static void aplicarNaTransacao(Handle handle) {
        handle.execute("SELECT set_config('app.tenant_id', ?, true)", tenantIdObrigatorio().toString());
    }

    private static Dados exigirDados() {
        Dados dados = ATUAL.get();
        if (dados == null) {
            throw new IllegalStateException("TenantContext acessado fora do ciclo de uma requisicao autenticada.");
        }
        return dados;
    }

    private record Dados(UUID usuarioId, Papel papel, UUID tenantId) {
    }
}

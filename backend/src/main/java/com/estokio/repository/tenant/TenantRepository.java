package com.estokio.repository.tenant;

import com.estokio.domain.tenant.Tenant;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.UUID;

/**
 * {@code tenant} nao tem RLS (e a propria unidade de isolamento, nao faz sentido
 * filtrado por si mesmo -- ver Multi-Tenancy e RLS). So o papel {@code app_platform}
 * cria/le tenants; rotas de loja recebem o {@code tenant_id} pronto via JWT.
 */
public final class TenantRepository {

    private static final String COLUNAS = "id, nome, slug, status, plano_id, criado_em";

    private final Jdbi jdbi;

    public TenantRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public List<Tenant> listarTodos() {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT " + COLUNAS + " FROM tenant ORDER BY criado_em DESC")
                .mapTo(Tenant.class)
                .list());
    }

    /** Checagem previa de slug duplicado, dentro da mesma transacao que vai criar o tenant. */
    public boolean existeSlug(Handle handle, String slug) {
        return handle.createQuery("SELECT EXISTS(SELECT 1 FROM tenant WHERE slug = :slug)")
                .bind("slug", slug)
                .mapTo(Boolean.class)
                .one();
    }

    /**
     * Insere o tenant (sempre nascendo {@code ATIVA}, ver contrato de {@code POST /api/plataforma/lojas})
     * e a {@code tenant_config} padrao (so os defaults da migration V3, sem overrides), na mesma
     * transacao ja aberta pelo chamador ({@code TenantService.criarLoja}).
     */
    public Tenant inserir(Handle handle, String nome, String slug, UUID planoId) {
        Tenant tenant = handle.createQuery(
                        "INSERT INTO tenant (id, nome, slug, status, plano_id) "
                                + "VALUES (gen_random_uuid(), :nome, :slug, 'ATIVA', :planoId) "
                                + "RETURNING " + COLUNAS)
                .bind("nome", nome)
                .bind("slug", slug)
                .bind("planoId", planoId)
                .mapTo(Tenant.class)
                .one();

        handle.createUpdate("INSERT INTO tenant_config (tenant_id) VALUES (:tenantId)")
                .bind("tenantId", tenant.id())
                .execute();

        return tenant;
    }
}

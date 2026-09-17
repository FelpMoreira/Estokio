package com.estokio.repository.tenant;

import com.estokio.domain.tenant.Plan;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code plano} nao tem RLS (e da propria plataforma, nao de um tenant especifico --
 * ver Multi-Tenancy e RLS). So o papel {@code app_platform} le/escreve esta tabela.
 */
public final class PlanoRepository {

    private static final String COLUNAS =
            "id, nome, max_produtos, max_pedidos_mes, max_usuarios, preco_centavos, ativo, criado_em";

    private final Jdbi jdbi;

    public PlanoRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public List<Plan> listarAtivos() {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT " + COLUNAS + " FROM plano WHERE ativo = true ORDER BY nome")
                .mapTo(Plan.class)
                .list());
    }

    /** Usado dentro da transacao de {@code TenantService.criarLoja} para validar o plano informado. */
    public Optional<Plan> buscarPorId(Handle handle, UUID id) {
        return handle.createQuery("SELECT " + COLUNAS + " FROM plano WHERE id = :id")
                .bind("id", id)
                .mapTo(Plan.class)
                .findOne();
    }
}

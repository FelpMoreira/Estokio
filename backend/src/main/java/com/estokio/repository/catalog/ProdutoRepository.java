package com.estokio.repository.catalog;

import com.estokio.domain.catalog.Produto;
import org.jdbi.v3.core.Handle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code produto} TEM RLS (ver [[Multi-Tenancy e RLS]]): toda query aqui depende do
 * {@code SET LOCAL app.tenant_id} ja aplicado na transacao pelo chamador
 * ({@link com.estokio.security.TenantContext#aplicarNaTransacao}) -- sem isso a
 * policy bloqueia silenciosamente (0 linhas, nao excecao). O filtro explicito por
 * {@code tenant_id} nos métodos que recebem o handle e defesa em profundidade, nao
 * a garantia real de isolamento.
 */
public final class ProdutoRepository {

    private static final String COLUNAS = "id, tenant_id, categoria_id, nome, descricao, ativo, criado_em";

    public Produto inserir(Handle handle, UUID tenantId, UUID categoriaId, String nome, String descricao) {
        return handle.createQuery(
                        "INSERT INTO produto (id, tenant_id, categoria_id, nome, descricao, ativo) "
                                + "VALUES (gen_random_uuid(), :tenantId, :categoriaId, :nome, :descricao, true) "
                                + "RETURNING " + COLUNAS)
                .bind("tenantId", tenantId)
                .bind("categoriaId", categoriaId)
                .bind("nome", nome)
                .bind("descricao", descricao)
                .mapTo(Produto.class)
                .one();
    }

    public List<Produto> listarPorTenant(Handle handle, UUID tenantId) {
        return handle.createQuery(
                        "SELECT " + COLUNAS + " FROM produto WHERE tenant_id = :tenantId ORDER BY criado_em DESC")
                .bind("tenantId", tenantId)
                .mapTo(Produto.class)
                .list();
    }

    public Optional<Produto> buscarPorId(Handle handle, UUID tenantId, UUID id) {
        return handle.createQuery(
                        "SELECT " + COLUNAS + " FROM produto WHERE id = :id AND tenant_id = :tenantId")
                .bind("id", id)
                .bind("tenantId", tenantId)
                .mapTo(Produto.class)
                .findOne();
    }
}

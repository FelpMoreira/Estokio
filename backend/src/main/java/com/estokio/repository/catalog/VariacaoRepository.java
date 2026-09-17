package com.estokio.repository.catalog;

import com.estokio.domain.catalog.Variacao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jdbi.v3.core.Handle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code variacao} TEM RLS (ver [[Multi-Tenancy e RLS]]) -- mesma ressalva de
 * {@link ProdutoRepository}. {@code atributos} e uma coluna {@code jsonb}; como o
 * projeto nao instala o plugin Jackson do Jdbi (mesmo padrao de {@code TenantServiceTest},
 * que le jsonb via {@code ::text}), este repositorio serializa/parseia manualmente com um
 * {@link ObjectMapper} local em vez de {@link org.jdbi.v3.core.mapper.reflect.ConstructorMapper}
 * (que nao sabe converter texto em {@code Map<String, String>}).
 */
public final class VariacaoRepository {

    private static final String COLUNAS =
            "id, tenant_id, produto_id, sku, atributos::text AS atributos, preco_centavos, ponto_reposicao, ativo";

    private static final ObjectMapper JSON = new ObjectMapper();

    public Variacao inserir(Handle handle, UUID tenantId, UUID produtoId, String sku,
                             Map<String, String> atributos, int precoCentavos, int pontoReposicao) {
        return handle.createQuery(
                        "INSERT INTO variacao (id, tenant_id, produto_id, sku, atributos, preco_centavos, ponto_reposicao, ativo) "
                                + "VALUES (gen_random_uuid(), :tenantId, :produtoId, :sku, CAST(:atributos AS JSONB), :precoCentavos, :pontoReposicao, true) "
                                + "RETURNING " + COLUNAS)
                .bind("tenantId", tenantId)
                .bind("produtoId", produtoId)
                .bind("sku", sku)
                .bind("atributos", serializar(atributos))
                .bind("precoCentavos", precoCentavos)
                .bind("pontoReposicao", pontoReposicao)
                .map((rs, ctx) -> mapear(rs))
                .one();
    }

    public boolean existeSku(Handle handle, UUID tenantId, String sku) {
        return handle.createQuery(
                        "SELECT EXISTS(SELECT 1 FROM variacao WHERE tenant_id = :tenantId AND sku = :sku)")
                .bind("tenantId", tenantId)
                .bind("sku", sku)
                .mapTo(Boolean.class)
                .one();
    }

    public Optional<Variacao> buscarPorId(Handle handle, UUID tenantId, UUID id) {
        return handle.createQuery(
                        "SELECT " + COLUNAS + " FROM variacao WHERE id = :id AND tenant_id = :tenantId")
                .bind("id", id)
                .bind("tenantId", tenantId)
                .map((rs, ctx) -> mapear(rs))
                .findOne();
    }

    public List<Variacao> listarPorProdutoIds(Handle handle, UUID tenantId, List<UUID> produtoIds) {
        if (produtoIds.isEmpty()) {
            return List.of();
        }
        return handle.createQuery(
                        "SELECT " + COLUNAS + " FROM variacao "
                                + "WHERE tenant_id = :tenantId AND produto_id = ANY(:produtoIds) ORDER BY sku")
                .bind("tenantId", tenantId)
                .bindArray("produtoIds", UUID.class, produtoIds.toArray(UUID[]::new))
                .map((rs, ctx) -> mapear(rs))
                .list();
    }

    /**
     * Atualiza so os campos regulares (preco, ponto de reposicao, ativo) -- nunca
     * estoque, que so muda via ledger. Campos nulos preservam o valor atual
     * ({@code COALESCE}), permitindo um PATCH parcial. Retorna vazio se o id nao
     * existir para este tenant (RLS ja garante isso; o {@code AND tenant_id} e so
     * defesa em profundidade).
     */
    public Optional<Variacao> atualizar(Handle handle, UUID tenantId, UUID id, Integer precoCentavos,
                                         Integer pontoReposicao, Boolean ativo) {
        return handle.createQuery(
                        "UPDATE variacao SET "
                                + "preco_centavos = COALESCE(:precoCentavos, preco_centavos), "
                                + "ponto_reposicao = COALESCE(:pontoReposicao, ponto_reposicao), "
                                + "ativo = COALESCE(:ativo, ativo) "
                                + "WHERE id = :id AND tenant_id = :tenantId "
                                + "RETURNING " + COLUNAS)
                .bind("precoCentavos", precoCentavos)
                .bind("pontoReposicao", pontoReposicao)
                .bind("ativo", ativo)
                .bind("id", id)
                .bind("tenantId", tenantId)
                .map((rs, ctx) -> mapear(rs))
                .findOne();
    }

    private Variacao mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Variacao(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("produto_id"),
                rs.getString("sku"),
                desserializar(rs.getString("atributos")),
                rs.getInt("preco_centavos"),
                rs.getInt("ponto_reposicao"),
                rs.getBoolean("ativo"));
    }

    private String serializar(Map<String, String> atributos) {
        try {
            return JSON.writeValueAsString(atributos == null ? Map.of() : atributos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar atributos da variacao.", e);
        }
    }

    private Map<String, String> desserializar(String atributosJson) {
        if (atributosJson == null || atributosJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(atributosJson, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao ler atributos da variacao.", e);
        }
    }
}

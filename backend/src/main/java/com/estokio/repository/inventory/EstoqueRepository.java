package com.estokio.repository.inventory;

import com.estokio.domain.inventory.EstoqueMovimento;
import com.estokio.domain.inventory.EstoqueSaldo;
import com.estokio.domain.inventory.EstoqueSaldoResumo;
import com.estokio.domain.inventory.TipoMovimento;
import org.jdbi.v3.core.Handle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code estoque_saldo} e {@code estoque_movimento} TEM RLS (ver [[Multi-Tenancy e RLS]])
 * -- mesma ressalva de {@link com.estokio.repository.catalog.ProdutoRepository}.
 *
 * <p>Ledger imutavel (ver [[Ledger de Estoque]]): so ha {@link #registrarMovimento}
 * (INSERT), nunca UPDATE/DELETE em {@code estoque_movimento} -- reforcado tambem por
 * trigger e REVOKE no banco (defesa em 3 camadas).</p>
 */
public final class EstoqueRepository {

    private static final String COLUNAS_SALDO = "variacao_id, tenant_id, qtd_fisica, qtd_reservada, atualizado_em";

    public void criarSaldoZerado(Handle handle, UUID tenantId, UUID variacaoId) {
        handle.createUpdate(
                        "INSERT INTO estoque_saldo (variacao_id, tenant_id, qtd_fisica, qtd_reservada) "
                                + "VALUES (:variacaoId, :tenantId, 0, 0)")
                .bind("variacaoId", variacaoId)
                .bind("tenantId", tenantId)
                .execute();
    }

    /**
     * Lock pessimista na linha de saldo (ver [[Reserva e Concorrencia]]) -- disciplina
     * exigida mesmo para um unico SKU, para nunca deixar entrada de estoque concorrente
     * (2 entradas na mesma variacao ao mesmo tempo) perder um incremento por causa de
     * leitura suja entre o SELECT e o UPDATE.
     */
    public Optional<EstoqueSaldo> buscarParaAtualizar(Handle handle, UUID tenantId, UUID variacaoId) {
        return handle.createQuery(
                        "SELECT " + COLUNAS_SALDO + " FROM estoque_saldo "
                                + "WHERE variacao_id = :variacaoId AND tenant_id = :tenantId FOR UPDATE")
                .bind("variacaoId", variacaoId)
                .bind("tenantId", tenantId)
                .mapTo(EstoqueSaldo.class)
                .findOne();
    }

    public void atualizarSaldoFisico(Handle handle, UUID variacaoId, int deltaFisico) {
        handle.createUpdate(
                        "UPDATE estoque_saldo SET qtd_fisica = qtd_fisica + :deltaFisico, atualizado_em = now() "
                                + "WHERE variacao_id = :variacaoId")
                .bind("deltaFisico", deltaFisico)
                .bind("variacaoId", variacaoId)
                .execute();
    }

    public EstoqueMovimento registrarMovimento(Handle handle, UUID tenantId, UUID variacaoId, TipoMovimento tipo,
                                                int deltaFisico, int deltaReservado, String motivo, UUID usuarioId) {
        return handle.createQuery("""
                        INSERT INTO estoque_movimento
                            (tenant_id, variacao_id, tipo, delta_fisico, delta_reservado, motivo, usuario_id)
                        VALUES (:tenantId, :variacaoId, :tipo, :deltaFisico, :deltaReservado, :motivo, :usuarioId)
                        RETURNING id, tenant_id, variacao_id, tipo, delta_fisico, delta_reservado, motivo,
                                  origem_tipo, origem_id, estorna_movimento_id, usuario_id, criado_em
                        """)
                .bind("tenantId", tenantId)
                .bind("variacaoId", variacaoId)
                .bind("tipo", tipo)
                .bind("deltaFisico", deltaFisico)
                .bind("deltaReservado", deltaReservado)
                .bind("motivo", motivo)
                .bind("usuarioId", usuarioId)
                .mapTo(EstoqueMovimento.class)
                .one();
    }

    public List<EstoqueSaldoResumo> listarResumo(Handle handle, UUID tenantId) {
        return handle.createQuery("""
                        SELECT v.id AS variacao_id, v.sku AS sku, p.nome AS nome_produto,
                               es.qtd_fisica AS qtd_fisica, es.qtd_reservada AS qtd_reservada,
                               (es.qtd_fisica - es.qtd_reservada) AS disponivel,
                               v.ponto_reposicao AS ponto_reposicao
                        FROM estoque_saldo es
                        JOIN variacao v ON v.id = es.variacao_id
                        JOIN produto p ON p.id = v.produto_id
                        WHERE es.tenant_id = :tenantId
                        ORDER BY p.nome, v.sku
                        """)
                .bind("tenantId", tenantId)
                .mapTo(EstoqueSaldoResumo.class)
                .list();
    }

    public Optional<EstoqueSaldoResumo> buscarResumoPorVariacao(Handle handle, UUID tenantId, UUID variacaoId) {
        return handle.createQuery("""
                        SELECT v.id AS variacao_id, v.sku AS sku, p.nome AS nome_produto,
                               es.qtd_fisica AS qtd_fisica, es.qtd_reservada AS qtd_reservada,
                               (es.qtd_fisica - es.qtd_reservada) AS disponivel,
                               v.ponto_reposicao AS ponto_reposicao
                        FROM estoque_saldo es
                        JOIN variacao v ON v.id = es.variacao_id
                        JOIN produto p ON p.id = v.produto_id
                        WHERE es.tenant_id = :tenantId AND v.id = :variacaoId
                        """)
                .bind("tenantId", tenantId)
                .bind("variacaoId", variacaoId)
                .mapTo(EstoqueSaldoResumo.class)
                .findOne();
    }
}

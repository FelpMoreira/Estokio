package com.estokio.inventory;

import com.estokio.PostgresTestBase;
import com.estokio.TestFixtures;
import com.estokio.domain.inventory.TipoMovimento;
import com.estokio.domain.user.Papel;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Secao 4.3 da spec: "somar o ledger inteiro a cada consulta nao escala" -- por isso
 * {@code estoque_saldo} e um snapshot mantido na MESMA transacao do movimento. Este
 * teste prova a parte que da confianca nesse atalho: depois de uma sequencia real de
 * movimentos (entrada, reserva, baixa parcial, liberacao, ajuste negativo, devolucao,
 * inventario), {@code SUM(delta_fisico)}/{@code SUM(delta_reservado)} agrupado por SKU
 * no ledger bate exatamente com o snapshot -- a mesma verificacao que a Fase 2 vai expor
 * como {@code POST /estoque/reconciliar}, aqui rodando contra dado gerado no proprio teste
 * (nao contra o que o SeedRunner deixou no banco, que pode mudar).
 */
class LedgerReconciliationTest extends PostgresTestBase {

    private static UUID tenantId;
    private static UUID usuarioId;

    @BeforeAll
    static void setUp() {
        Jdbi migrator = migratorJdbi();
        tenantId = TestFixtures.criarTenant(migrator);
        usuarioId = TestFixtures.criarUsuarioAtivo(migrator, tenantId, Papel.ADMIN_LOJA, "senha123").id();
    }

    private void aplicarMovimento(Jdbi appUser, UUID variacaoId, TipoMovimento tipo, int deltaFisico, int deltaReservado) {
        // Mesmo padrao do SeedRunner e da futura service de estoque (Fase 1): movimento
        // e atualizacao do snapshot na MESMA transacao (Decisao #1 do dominio).
        comoTenant(appUser, tenantId, (Handle handle) -> {
            handle.createUpdate("""
                            INSERT INTO estoque_movimento (tenant_id, variacao_id, tipo, delta_fisico, delta_reservado, usuario_id)
                            VALUES (:tenantId, :variacaoId, :tipo, :deltaFisico, :deltaReservado, :usuarioId)
                            """)
                    .bind("tenantId", tenantId).bind("variacaoId", variacaoId).bind("tipo", tipo)
                    .bind("deltaFisico", deltaFisico).bind("deltaReservado", deltaReservado)
                    .bind("usuarioId", usuarioId)
                    .execute();
            handle.createUpdate("""
                            UPDATE estoque_saldo
                            SET qtd_fisica = qtd_fisica + :deltaFisico,
                                qtd_reservada = qtd_reservada + :deltaReservado,
                                atualizado_em = now()
                            WHERE variacao_id = :variacaoId
                            """)
                    .bind("deltaFisico", deltaFisico).bind("deltaReservado", deltaReservado)
                    .bind("variacaoId", variacaoId)
                    .execute();
            return null;
        });
    }

    @Test
    void somaDoLedgerBateComOSnapshotAposSequenciaDeMovimentos() {
        Jdbi migrator = migratorJdbi();
        Jdbi appUser = appUserJdbi();

        UUID variacao1 = TestFixtures.criarVariacaoComSaldo(migrator, tenantId, 0, 0);
        UUID variacao2 = TestFixtures.criarVariacaoComSaldo(migrator, tenantId, 0, 0);

        // variacao1: entrada -> reserva -> baixa parcial da reserva -> perda por ajuste
        // -> devolucao do cliente -> recontagem de inventario para cima.
        aplicarMovimento(appUser, variacao1, TipoMovimento.ENTRADA, 50, 0);      // (50, 0)
        aplicarMovimento(appUser, variacao1, TipoMovimento.RESERVA, 0, 10);      // (50, 10)
        aplicarMovimento(appUser, variacao1, TipoMovimento.BAIXA, -10, -10);     // (40, 0)
        aplicarMovimento(appUser, variacao1, TipoMovimento.AJUSTE, -5, 0);       // (35, 0) -- quebra/perda
        aplicarMovimento(appUser, variacao1, TipoMovimento.DEVOLUCAO, 3, 0);     // (38, 0)
        aplicarMovimento(appUser, variacao1, TipoMovimento.INVENTARIO, 2, 0);    // (40, 0) -- recontagem

        // variacao2: entrada -> reserva total (disponivel = 0) -> liberacao parcial -> ajuste negativo.
        aplicarMovimento(appUser, variacao2, TipoMovimento.ENTRADA, 20, 0);      // (20, 0)
        aplicarMovimento(appUser, variacao2, TipoMovimento.RESERVA, 0, 20);      // (20, 20)
        aplicarMovimento(appUser, variacao2, TipoMovimento.LIBERACAO, 0, -8);    // (20, 12)
        aplicarMovimento(appUser, variacao2, TipoMovimento.AJUSTE, -3, 0);       // (17, 12)

        Map<UUID, int[]> esperado = Map.of(
                variacao1, new int[]{40, 0},
                variacao2, new int[]{17, 12});

        assertReconciliaExatamente(appUser, esperado);
    }

    private void assertReconciliaExatamente(Jdbi appUser, Map<UUID, int[]> esperadoPorVariacao) {
        UUID[] variacaoIds = esperadoPorVariacao.keySet().toArray(UUID[]::new);

        Map<UUID, int[]> somaDoLedger = comoTenant(appUser, tenantId, handle -> {
            Map<UUID, int[]> resultado = new HashMap<>();
            handle.createQuery("""
                            SELECT variacao_id, SUM(delta_fisico) AS soma_fisico, SUM(delta_reservado) AS soma_reservado
                            FROM estoque_movimento
                            WHERE variacao_id = ANY(:variacaoIds)
                            GROUP BY variacao_id
                            """)
                    .bindArray("variacaoIds", UUID.class, variacaoIds)
                    .map((rs, ctx) -> {
                        UUID variacaoId = (UUID) rs.getObject("variacao_id");
                        int somaFisico = rs.getInt("soma_fisico");
                        int somaReservado = rs.getInt("soma_reservado");
                        resultado.put(variacaoId, new int[]{somaFisico, somaReservado});
                        return null;
                    })
                    .list();
            return resultado;
        });

        Map<UUID, int[]> snapshot = comoTenant(appUser, tenantId, handle -> {
            Map<UUID, int[]> resultado = new HashMap<>();
            handle.createQuery("""
                            SELECT variacao_id, qtd_fisica, qtd_reservada
                            FROM estoque_saldo
                            WHERE variacao_id = ANY(:variacaoIds)
                            """)
                    .bindArray("variacaoIds", UUID.class, variacaoIds)
                    .map((rs, ctx) -> {
                        UUID variacaoId = (UUID) rs.getObject("variacao_id");
                        resultado.put(variacaoId, new int[]{rs.getInt("qtd_fisica"), rs.getInt("qtd_reservada")});
                        return null;
                    })
                    .list();
            return resultado;
        });

        for (Map.Entry<UUID, int[]> entry : esperadoPorVariacao.entrySet()) {
            UUID variacaoId = entry.getKey();
            int[] esperado = entry.getValue();

            assertEquals(esperado[0], snapshot.get(variacaoId)[0],
                    "qtd_fisica do snapshot difere do esperado para " + variacaoId);
            assertEquals(esperado[1], snapshot.get(variacaoId)[1],
                    "qtd_reservada do snapshot difere do esperado para " + variacaoId);

            assertEquals(snapshot.get(variacaoId)[0], somaDoLedger.get(variacaoId)[0],
                    "SUM(delta_fisico) do ledger nao bate com o snapshot para " + variacaoId);
            assertEquals(snapshot.get(variacaoId)[1], somaDoLedger.get(variacaoId)[1],
                    "SUM(delta_reservado) do ledger nao bate com o snapshot para " + variacaoId);
        }
    }
}

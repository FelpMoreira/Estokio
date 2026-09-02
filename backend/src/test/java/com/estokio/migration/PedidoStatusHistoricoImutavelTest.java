package com.estokio.migration;

import com.estokio.PostgresTestBase;
import com.estokio.TestFixtures;
import com.estokio.domain.user.Papel;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Achado do agente {@code revisor} na revisao da Fase 0, corrigido na migration V18:
 * {@code pedido_status_historico} e o log de auditoria de quem/quando/por-que um pedido
 * mudou de status (secao 6.3 da spec) e precisa da mesma blindagem append-only do
 * ledger de estoque. Mesma dupla camada testada em {@link EstoqueMovimentoImutavelTest}.
 */
class PedidoStatusHistoricoImutavelTest extends PostgresTestBase {

    private static UUID tenantId;
    private static UUID clienteId;
    private static UUID pedidoId;

    @BeforeAll
    static void setUp() {
        Jdbi migrator = migratorJdbi();
        tenantId = TestFixtures.criarTenant(migrator);
        clienteId = TestFixtures.criarUsuarioAtivo(migrator, null, Papel.CLIENTE, "senha123").id();
        pedidoId = migrator.inTransaction(handle ->
                TestFixtures.criarPedido(handle, tenantId, clienteId, 1, null));
    }

    private UUID inserirHistorico(Jdbi jdbi) {
        return comoTenant(jdbi, tenantId, handle -> handle.createQuery("""
                        INSERT INTO pedido_status_historico (tenant_id, pedido_id, status_de, status_para)
                        VALUES (:tenantId, :pedidoId, NULL, 'PENDENTE')
                        RETURNING id
                        """)
                .bind("tenantId", tenantId)
                .bind("pedidoId", pedidoId)
                .mapTo(UUID.class)
                .one());
    }

    @Test
    void appUserConsegueInserirHistorico() {
        UUID id = inserirHistorico(appUserJdbi());
        assertTrue(id != null);
    }

    @Test
    void appUserNaoTemPrivilegioParaAtualizarHistorico() {
        Jdbi appUser = appUserJdbi();
        UUID id = inserirHistorico(appUser);

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> comoTenant(appUser, tenantId, handle -> handle.execute(
                        "UPDATE pedido_status_historico SET motivo = 'forjado' WHERE id = ?", id)));

        assertTrue(erro.getMessage().toLowerCase().contains("permission denied"),
                "Esperava erro de falta de privilegio, recebeu: " + erro.getMessage());
    }

    @Test
    void appUserNaoTemPrivilegioParaApagarHistorico() {
        Jdbi appUser = appUserJdbi();
        UUID id = inserirHistorico(appUser);

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> comoTenant(appUser, tenantId, handle -> handle.execute(
                        "DELETE FROM pedido_status_historico WHERE id = ?", id)));

        assertTrue(erro.getMessage().toLowerCase().contains("permission denied"),
                "Esperava erro de falta de privilegio, recebeu: " + erro.getMessage());
    }

    @Test
    void triggerBarraUpdateMesmoParaRoleComPrivilegio() {
        Jdbi migrator = migratorJdbi();
        UUID id = inserirHistorico(migrator);

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> migrator.useHandle(handle -> handle.execute(
                        "UPDATE pedido_status_historico SET motivo = 'forjado' WHERE id = ?", id)));

        assertTrue(erro.getMessage().toLowerCase().contains("append-only"),
                "Esperava mensagem da trigger de imutabilidade, recebeu: " + erro.getMessage());
    }

    @Test
    void triggerBarraDeleteMesmoParaRoleComPrivilegio() {
        Jdbi migrator = migratorJdbi();
        UUID id = inserirHistorico(migrator);

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> migrator.useHandle(handle -> handle.execute(
                        "DELETE FROM pedido_status_historico WHERE id = ?", id)));

        assertTrue(erro.getMessage().toLowerCase().contains("append-only"),
                "Esperava mensagem da trigger de imutabilidade, recebeu: " + erro.getMessage());
    }
}

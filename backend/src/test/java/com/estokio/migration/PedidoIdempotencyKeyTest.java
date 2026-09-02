package com.estokio.migration;

import com.estokio.PostgresTestBase;
import com.estokio.TestFixtures;
import com.estokio.domain.user.Papel;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Decisao de kickoff #9: {@code idempotency_key} mora em {@code pedido}, nao no
 * movimento de estoque, com {@code UNIQUE(idempotency_key)} (V12). O fluxo HTTP de
 * {@code POST /pedidos} (Fase 1) ainda nao existe, mas a garantia de estrutura -- duplo
 * clique/retry de rede nunca gera dois pedidos com a mesma chave -- e testavel hoje
 * direto no schema, via INSERT.
 */
class PedidoIdempotencyKeyTest extends PostgresTestBase {

    private static UUID tenantId;
    private static UUID clienteId;

    @BeforeAll
    static void setUp() {
        Jdbi migrator = migratorJdbi();
        tenantId = TestFixtures.criarTenant(migrator);
        clienteId = TestFixtures.criarUsuarioAtivo(migrator, null, Papel.CLIENTE, "senha123").id();
    }

    @Test
    void segundaInsercaoComMesmaChaveNaoNulaEhRejeitada() {
        Jdbi migrator = migratorJdbi();
        String chave = "idem-" + UUID.randomUUID();

        migrator.useHandle(handle -> TestFixtures.criarPedido(handle, tenantId, clienteId, 5001, chave));

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> migrator.useHandle(handle -> TestFixtures.criarPedido(handle, tenantId, clienteId, 5002, chave)));

        assertEquals(true, erro.getMessage().toLowerCase().contains("idempotency_key")
                || erro.getMessage().toLowerCase().contains("pedido_idempotency_key_key"),
                "Esperava violacao do UNIQUE(idempotency_key), recebeu: " + erro.getMessage());
    }

    @Test
    void doisPedidosComChaveNulaSaoPermitidos() {
        // Caso de borda: pedidos fora do fluxo idempotente (ex: os do SeedRunner) tem
        // idempotency_key NULL -- e SQL padrao trata NULLs como distintos num UNIQUE,
        // entao multiplos NULLs nao colidem entre si. Documenta esse comportamento
        // explicitamente em vez de assumi-lo.
        Jdbi migrator = migratorJdbi();

        UUID pedido1 = migrator.withHandle(handle -> TestFixtures.criarPedido(handle, tenantId, clienteId, 6001, null));
        UUID pedido2 = migrator.withHandle(handle -> TestFixtures.criarPedido(handle, tenantId, clienteId, 6002, null));

        assertNotEquals(pedido1, pedido2);
    }

    @Test
    void mesmaChaveEmTenantsDiferentesTambemColide() {
        // A UNIQUE constraint e global (nao composta com tenant_id) -- decisao deliberada
        // de kickoff #9, ja que a chave e gerada pelo cliente (alta entropia esperada) e
        // nao ha necessidade de reuso entre lojas. Prova que o schema realmente aplica isso.
        Jdbi migrator = migratorJdbi();
        UUID outroTenantId = TestFixtures.criarTenant(migrator);
        UUID outroClienteId = TestFixtures.criarUsuarioAtivo(migrator, null, Papel.CLIENTE, "senha123").id();
        String chave = "idem-cross-tenant-" + UUID.randomUUID();

        migrator.useHandle(handle -> TestFixtures.criarPedido(handle, tenantId, clienteId, 7001, chave));

        assertThrows(UnableToExecuteStatementException.class, () -> migrator.useHandle(handle ->
                TestFixtures.criarPedido(handle, outroTenantId, outroClienteId, 7002, chave)));
    }
}

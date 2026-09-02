package com.estokio.migration;

import com.estokio.PostgresTestBase;
import com.estokio.TestFixtures;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rede de seguranca da secao 5.3 da spec: mesmo que o lock pessimista de aplicacao
 * (Fase 1, ainda nao implementado) tenha um bug, o banco recusa qualquer estado de
 * {@code estoque_saldo} onde fisico, reservado ou disponivel fiquem negativos --
 * ver V9 da migration. Overselling vira erro de banco, nunca dado corrompido.
 */
class EstoqueSaldoCheckConstraintTest extends PostgresTestBase {

    private static UUID tenantId;

    @BeforeAll
    static void setUp() {
        tenantId = TestFixtures.criarTenant(migratorJdbi());
    }

    private UUID novaVariacao(Jdbi migrator) {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        return migrator.inTransaction(handle -> {
            UUID categoriaId = handle.createQuery("""
                            INSERT INTO categoria (id, tenant_id, nome, ativo)
                            VALUES (gen_random_uuid(), :tenantId, 'Cat', true) RETURNING id
                            """).bind("tenantId", tenantId).mapTo(UUID.class).one();
            UUID produtoId = handle.createQuery("""
                            INSERT INTO produto (id, tenant_id, categoria_id, nome, ativo)
                            VALUES (gen_random_uuid(), :tenantId, :categoriaId, 'Prod', true) RETURNING id
                            """).bind("tenantId", tenantId).bind("categoriaId", categoriaId).mapTo(UUID.class).one();
            return handle.createQuery("""
                            INSERT INTO variacao (id, tenant_id, produto_id, sku, preco_centavos, ativo)
                            VALUES (gen_random_uuid(), :tenantId, :produtoId, :sku, 1000, true) RETURNING id
                            """).bind("tenantId", tenantId).bind("produtoId", produtoId).bind("sku", "SKU-" + sufixo)
                    .mapTo(UUID.class).one();
        });
    }

    private void inserirSaldo(Jdbi migrator, UUID variacaoId, int qtdFisica, int qtdReservada) {
        migrator.useHandle(handle -> handle.createUpdate("""
                        INSERT INTO estoque_saldo (variacao_id, tenant_id, qtd_fisica, qtd_reservada)
                        VALUES (:variacaoId, :tenantId, :qtdFisica, :qtdReservada)
                        """)
                .bind("variacaoId", variacaoId)
                .bind("tenantId", tenantId)
                .bind("qtdFisica", qtdFisica)
                .bind("qtdReservada", qtdReservada)
                .execute());
    }

    @Test
    void permiteSaldoZeradoNasDuasPontas() {
        // Caso de borda: fisico=0 e reservado=0 (SKU recem-criado, sem estoque ainda) e valido.
        Jdbi migrator = migratorJdbi();
        UUID variacaoId = novaVariacao(migrator);
        inserirSaldo(migrator, variacaoId, 0, 0);

        int total = migrator.withHandle(handle -> handle.createQuery(
                        "SELECT count(*) FROM estoque_saldo WHERE variacao_id = ?")
                .bind(0, variacaoId)
                .mapTo(Integer.class)
                .one());
        assertTrue(total == 1);
    }

    @Test
    void permiteFisicoIgualReservado() {
        // Caso de borda: disponivel = 0 exatamente (tudo reservado) e valido, so nao pode ficar negativo.
        Jdbi migrator = migratorJdbi();
        UUID variacaoId = novaVariacao(migrator);
        inserirSaldo(migrator, variacaoId, 5, 5);
    }

    @Test
    void rejeitaQtdFisicaNegativa() {
        Jdbi migrator = migratorJdbi();
        UUID variacaoId = novaVariacao(migrator);

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> inserirSaldo(migrator, variacaoId, -1, 0));

        // fisica=-1 com reservada=0 viola simultaneamente a CHECK de fisica nao-negativa
        // e a de disponivel nao-negativo (disponivel = fisica - reservada = -1 tambem).
        // Postgres reporta so a primeira que avalia; qual das duas nao importa para este
        // teste -- o que importa e que o INSERT foi recusado por uma CHECK de saldo.
        assertTrue(erro.getMessage().contains("chk_estoque_saldo_fisica_nao_negativa")
                        || erro.getMessage().contains("chk_estoque_saldo_disponivel_nao_negativo"),
                "Esperava violacao de uma CHECK de saldo, recebeu: " + erro.getMessage());
    }

    @Test
    void rejeitaQtdReservadaNegativa() {
        Jdbi migrator = migratorJdbi();
        UUID variacaoId = novaVariacao(migrator);

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> inserirSaldo(migrator, variacaoId, 5, -1));

        assertTrue(erro.getMessage().contains("chk_estoque_saldo_reservada_nao_negativa"),
                "Esperava violacao da CHECK de reservada nao-negativa, recebeu: " + erro.getMessage());
    }

    @Test
    void rejeitaDisponivelNegativoMesmoComFisicaEReservadaNaoNegativas() {
        // fisica=5, reservada=6: nenhuma das duas e negativa isoladamente, mas o disponivel
        // (fisica - reservada = -1) seria -- exatamente o caso que a 3a CHECK cobre.
        Jdbi migrator = migratorJdbi();
        UUID variacaoId = novaVariacao(migrator);

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> inserirSaldo(migrator, variacaoId, 5, 6));

        assertTrue(erro.getMessage().contains("chk_estoque_saldo_disponivel_nao_negativo"),
                "Esperava violacao da CHECK de disponivel nao-negativo, recebeu: " + erro.getMessage());
    }

    @Test
    void rejeitaUpdateQueLevariaDisponivelParaNegativo() {
        // Reproduz o cenario real: uma sequencia de movimentos (nao so o INSERT inicial)
        // tentando reservar mais do que o fisico suporta.
        Jdbi migrator = migratorJdbi();
        UUID variacaoId = novaVariacao(migrator);
        inserirSaldo(migrator, variacaoId, 3, 3); // tudo ja reservado, disponivel = 0

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> migrator.useHandle(handle -> handle.createUpdate(
                                "UPDATE estoque_saldo SET qtd_reservada = qtd_reservada + 1 WHERE variacao_id = :variacaoId")
                        .bind("variacaoId", variacaoId)
                        .execute()));

        assertTrue(erro.getMessage().contains("chk_estoque_saldo_disponivel_nao_negativo"),
                "Esperava violacao da CHECK de disponivel nao-negativo, recebeu: " + erro.getMessage());
    }
}

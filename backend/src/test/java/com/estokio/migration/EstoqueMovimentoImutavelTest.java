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
 * Blindagem 1 (regra de ouro do projeto: {@code quantidade} nunca e editavel) em
 * {@code estoque_movimento} -- ver secao 4.2 da spec e V11 da migration. Cobre as
 * duas camadas de defesa: falta de privilegio de UPDATE/DELETE para {@code app_user}
 * (V17) e a trigger {@code bloquear_alteracao_estoque_movimento} (V11), que barra
 * mesmo um role com privilegio (o dono {@code migrator}).
 */
class EstoqueMovimentoImutavelTest extends PostgresTestBase {

    private static UUID tenantId;
    private static UUID usuarioId;
    private static UUID variacaoId;

    @BeforeAll
    static void setUp() {
        Jdbi migrator = migratorJdbi();
        tenantId = TestFixtures.criarTenant(migrator);
        usuarioId = TestFixtures.criarUsuarioAtivo(migrator, tenantId, com.estokio.domain.user.Papel.ADMIN_LOJA, "senha123").id();
        variacaoId = TestFixtures.criarVariacaoComSaldo(migrator, tenantId, 0, 0);
    }

    private long inserirMovimento(Jdbi jdbi) {
        return comoTenant(jdbi, tenantId, handle -> handle.createQuery("""
                        INSERT INTO estoque_movimento (tenant_id, variacao_id, tipo, delta_fisico, delta_reservado, usuario_id)
                        VALUES (:tenantId, :variacaoId, 'ENTRADA', 10, 0, :usuarioId)
                        RETURNING id
                        """)
                .bind("tenantId", tenantId)
                .bind("variacaoId", variacaoId)
                .bind("usuarioId", usuarioId)
                .mapTo(Long.class)
                .one());
    }

    @Test
    void appUserConsegueInserirMovimento() {
        long id = inserirMovimento(appUserJdbi());
        assertTrue(id > 0);
    }

    @Test
    void appUserNaoTemPrivilegioParaAtualizarMovimento() {
        Jdbi appUser = appUserJdbi();
        long id = inserirMovimento(appUser);

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> comoTenant(appUser, tenantId, handle -> handle.execute(
                        "UPDATE estoque_movimento SET delta_fisico = 999 WHERE id = ?", id)));

        assertTrue(erro.getMessage().toLowerCase().contains("permission denied"),
                "Esperava erro de falta de privilegio, recebeu: " + erro.getMessage());
    }

    @Test
    void appUserNaoTemPrivilegioParaApagarMovimento() {
        Jdbi appUser = appUserJdbi();
        long id = inserirMovimento(appUser);

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> comoTenant(appUser, tenantId, handle -> handle.execute(
                        "DELETE FROM estoque_movimento WHERE id = ?", id)));

        assertTrue(erro.getMessage().toLowerCase().contains("permission denied"),
                "Esperava erro de falta de privilegio, recebeu: " + erro.getMessage());
    }

    /**
     * Segunda camada: mesmo o dono da tabela ({@code migrator}, que TEM privilegio de
     * UPDATE/DELETE) e barrado pela trigger. Prova que a imutabilidade nao depende so
     * do GRANT/REVOKE -- se algum dia um privilegio for concedido por engano a
     * app_user, a trigger ainda segura.
     */
    @Test
    void triggerBarraUpdateMesmoParaRoleComPrivilegio() {
        Jdbi migrator = migratorJdbi();
        long id = inserirMovimento(migrator);

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> migrator.useHandle(handle -> handle.execute(
                        "UPDATE estoque_movimento SET delta_fisico = 999 WHERE id = ?", id)));

        assertTrue(erro.getMessage().toLowerCase().contains("append-only"),
                "Esperava mensagem da trigger de imutabilidade, recebeu: " + erro.getMessage());
    }

    @Test
    void triggerBarraDeleteMesmoParaRoleComPrivilegio() {
        Jdbi migrator = migratorJdbi();
        long id = inserirMovimento(migrator);

        UnableToExecuteStatementException erro = assertThrows(UnableToExecuteStatementException.class,
                () -> migrator.useHandle(handle -> handle.execute(
                        "DELETE FROM estoque_movimento WHERE id = ?", id)));

        assertTrue(erro.getMessage().toLowerCase().contains("append-only"),
                "Esperava mensagem da trigger de imutabilidade, recebeu: " + erro.getMessage());
    }
}

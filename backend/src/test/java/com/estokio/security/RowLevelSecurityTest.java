package com.estokio.security;

import com.estokio.PostgresTestBase;
import com.estokio.TestFixtures;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defesa em profundidade #4 da spec (secao 7): Row Level Security como rede de
 * seguranca caso um repositorio esqueca o filtro de {@code tenant_id} em codigo.
 * Testa exatamente o mecanismo real de {@link com.estokio.security.TenantContext#aplicarNaTransacao}
 * (aqui reproduzido via {@link PostgresTestBase#comoTenant}), nao um substituto.
 */
class RowLevelSecurityTest extends PostgresTestBase {

    private static UUID tenantA;
    private static UUID tenantB;

    @BeforeAll
    static void setUp() {
        Jdbi migrator = migratorJdbi();
        tenantA = TestFixtures.criarTenant(migrator);
        tenantB = TestFixtures.criarTenant(migrator);

        criarProdutos(migrator, tenantA, "Produto A1", "Produto A2");
        criarProdutos(migrator, tenantB, "Produto B1", "Produto B2", "Produto B3");
    }

    private static void criarProdutos(Jdbi migrator, UUID tenantId, String... nomes) {
        migrator.useTransaction(handle -> {
            for (String nome : nomes) {
                handle.createUpdate("""
                                INSERT INTO produto (id, tenant_id, nome, ativo)
                                VALUES (gen_random_uuid(), :tenantId, :nome, true)
                                """)
                        .bind("tenantId", tenantId)
                        .bind("nome", nome)
                        .execute();
            }
        });
    }

    private List<String> nomesVisiveis(Jdbi jdbi, UUID tenantIdOuNulo) {
        if (tenantIdOuNulo == null) {
            return semTenant(jdbi, handle -> handle.createQuery("SELECT nome FROM produto ORDER BY nome")
                    .mapTo(String.class)
                    .list());
        }
        return comoTenant(jdbi, tenantIdOuNulo, handle -> handle.createQuery("SELECT nome FROM produto ORDER BY nome")
                .mapTo(String.class)
                .list());
    }

    @Test
    void appUserComTenantASoVeProdutosDoTenantA() {
        List<String> nomes = nomesVisiveis(appUserJdbi(), tenantA);
        assertEquals(List.of("Produto A1", "Produto A2"), nomes);
    }

    @Test
    void appUserComTenantBSoVeProdutosDoTenantB() {
        List<String> nomes = nomesVisiveis(appUserJdbi(), tenantB);
        assertEquals(List.of("Produto B1", "Produto B2", "Produto B3"), nomes);
    }

    @Test
    void appUserComTenantANuncaVeLinhaDoTenantB() {
        List<String> nomes = nomesVisiveis(appUserJdbi(), tenantA);
        assertTrue(nomes.stream().noneMatch(nome -> nome.startsWith("Produto B")),
                "Vazamento de tenant: tenant A enxergou " + nomes);
    }

    @Test
    void appUserSemSetLocalNaoVeNenhumaLinha_defaultDeny() {
        // app_user autenticado, mas sem SET LOCAL app.tenant_id -- current_setting(...,true)
        // retorna string vazia, que nunca bate com nenhum tenant_id::uuid real. Default-deny,
        // nao "ve tudo por engano".
        List<String> nomes = nomesVisiveis(appUserJdbi(), null);
        assertEquals(List.of(), nomes);
    }

    @Test
    void appPlatformBypassaRlsEVeLinhasDeMultiplosTenants() {
        // app_platform tem BYPASSRLS (Decisao de kickoff #10) -- usado so em /api/plataforma/**,
        // com queries agregadas na camada de servico, mas o banco em si PERMITE ver tudo.
        // Filtra pelos 2 tenants deste teste (em vez de "SELECT * FROM produto" sem filtro)
        // porque o container Postgres e compartilhado por toda a suite (ver PostgresTestBase):
        // outras classes de teste tambem inserem produto/variacao no mesmo banco.
        Jdbi appPlatform = appPlatformJdbi();
        List<String> nomes = appPlatform.withHandle(handle -> handle.createQuery(
                        "SELECT nome FROM produto WHERE tenant_id = ANY(:tenantIds) ORDER BY nome")
                .bindArray("tenantIds", UUID.class, tenantA, tenantB)
                .mapTo(String.class)
                .list());

        assertEquals(List.of("Produto A1", "Produto A2", "Produto B1", "Produto B2", "Produto B3"), nomes);
    }

    @Test
    void appUserComTenantSemNenhumProdutoVeListaVaziaSemErro() {
        // Caso de borda: tenant existente e valido, mas sem nenhuma linha na tabela --
        // deve ser uma lista vazia normal, distinta do bloqueio "sem SET LOCAL" acima
        // (mesmo resultado observavel, causas diferentes; ambos precisam funcionar).
        UUID tenantVazio = TestFixtures.criarTenant(migratorJdbi());
        List<String> nomes = nomesVisiveis(appUserJdbi(), tenantVazio);
        assertEquals(List.of(), nomes);
    }
}

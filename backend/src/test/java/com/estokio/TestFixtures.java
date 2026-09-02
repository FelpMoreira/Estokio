package com.estokio;

import com.estokio.domain.user.Papel;
import com.estokio.domain.user.Usuario;
import com.estokio.repository.auth.UsuarioRepository;
import com.estokio.security.PasswordEncoder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.time.Instant;
import java.util.UUID;

/**
 * Fabrica de dado de teste. Usa sempre o Jdbi do papel {@code migrator} (dono das
 * tabelas) para montar fixtures entre tenants sem fricao de RLS: o dono de uma tabela
 * ignora as policies de RLS por padrao no Postgres (nenhuma migration usa
 * {@code FORCE ROW LEVEL SECURITY}), entao inserir dado de tenant A e B na mesma
 * chamada nao exige alternar {@code SET LOCAL app.tenant_id} a cada INSERT.
 *
 * <p>Os testes que exercitam RLS de fato leem/escrevem depois via
 * {@link PostgresTestBase#appUserJdbi()} / {@code appPlatformJdbi()}, nunca via fixture.</p>
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static UUID criarPlano(Jdbi migratorJdbi, String nome) {
        return migratorJdbi.withHandle(handle -> handle.createQuery("""
                        INSERT INTO plano (id, nome, max_produtos, max_pedidos_mes, max_usuarios, preco_centavos, ativo)
                        VALUES (gen_random_uuid(), :nome, 100, 1000, 5, 0, true)
                        RETURNING id
                        """)
                .bind("nome", nome)
                .mapTo(UUID.class)
                .one());
    }

    public static UUID criarTenant(Jdbi migratorJdbi, UUID planoId) {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        return migratorJdbi.withHandle(handle -> handle.createQuery("""
                        INSERT INTO tenant (id, nome, slug, status, plano_id)
                        VALUES (gen_random_uuid(), :nome, :slug, 'ATIVA', :planoId)
                        RETURNING id
                        """)
                .bind("nome", "Loja Teste " + sufixo)
                .bind("slug", "loja-teste-" + sufixo)
                .bind("planoId", planoId)
                .mapTo(UUID.class)
                .one());
    }

    /** Cria plano + tenant numa tacada so; a maioria dos testes nao se importa com o plano. */
    public static UUID criarTenant(Jdbi migratorJdbi) {
        UUID planoId = criarPlano(migratorJdbi, "Plano Teste " + UUID.randomUUID().toString().substring(0, 8));
        return criarTenant(migratorJdbi, planoId);
    }

    public static Usuario criarUsuario(Jdbi migratorJdbi, UUID tenantId, Papel papel, String senhaPura, boolean ativo) {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        String email = "usuario-" + sufixo + "@estokio-test.dev";
        UsuarioRepository repositorio = new UsuarioRepository(migratorJdbi);
        return repositorio.inserir(new Usuario(
                UUID.randomUUID(), tenantId, "Usuario Teste " + sufixo, email,
                PasswordEncoder.hash(senhaPura), papel, ativo, Instant.now()));
    }

    public static Usuario criarUsuarioAtivo(Jdbi migratorJdbi, UUID tenantId, Papel papel, String senhaPura) {
        return criarUsuario(migratorJdbi, tenantId, papel, senhaPura, true);
    }

    public static UUID criarVariacaoComSaldo(Jdbi migratorJdbi, UUID tenantId, int qtdFisica, int qtdReservada) {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        return migratorJdbi.inTransaction(handle -> {
            UUID categoriaId = handle.createQuery("""
                            INSERT INTO categoria (id, tenant_id, nome, ativo) VALUES (gen_random_uuid(), :tenantId, 'Categoria Teste', true)
                            RETURNING id
                            """)
                    .bind("tenantId", tenantId).mapTo(UUID.class).one();
            UUID produtoId = handle.createQuery("""
                            INSERT INTO produto (id, tenant_id, categoria_id, nome, ativo) VALUES (gen_random_uuid(), :tenantId, :categoriaId, 'Produto Teste', true)
                            RETURNING id
                            """)
                    .bind("tenantId", tenantId).bind("categoriaId", categoriaId).mapTo(UUID.class).one();
            UUID variacaoId = handle.createQuery("""
                            INSERT INTO variacao (id, tenant_id, produto_id, sku, preco_centavos, ativo)
                            VALUES (gen_random_uuid(), :tenantId, :produtoId, :sku, 1000, true)
                            RETURNING id
                            """)
                    .bind("tenantId", tenantId).bind("produtoId", produtoId).bind("sku", "SKU-" + sufixo)
                    .mapTo(UUID.class).one();
            handle.createUpdate("""
                            INSERT INTO estoque_saldo (variacao_id, tenant_id, qtd_fisica, qtd_reservada)
                            VALUES (:variacaoId, :tenantId, :qtdFisica, :qtdReservada)
                            """)
                    .bind("variacaoId", variacaoId).bind("tenantId", tenantId)
                    .bind("qtdFisica", qtdFisica).bind("qtdReservada", qtdReservada)
                    .execute();
            return variacaoId;
        });
    }

    /** Pedido minimo valido, sem itens (suficiente para os testes de estrutura de Fase 0). */
    public static UUID criarPedido(Handle handle, UUID tenantId, UUID clienteId, int numero, String idempotencyKey) {
        return handle.createQuery("""
                        INSERT INTO pedido
                            (id, tenant_id, cliente_id, numero, status, subtotal_centavos, frete_centavos,
                             total_centavos, forma_pagamento, endereco, idempotency_key)
                        VALUES
                            (gen_random_uuid(), :tenantId, :clienteId, :numero, 'PENDENTE', 1000, 0, 1000,
                             'PIX', '{}'::jsonb, :idempotencyKey)
                        RETURNING id
                        """)
                .bind("tenantId", tenantId)
                .bind("clienteId", clienteId)
                .bind("numero", numero)
                .bind("idempotencyKey", idempotencyKey)
                .mapTo(UUID.class)
                .one();
    }
}

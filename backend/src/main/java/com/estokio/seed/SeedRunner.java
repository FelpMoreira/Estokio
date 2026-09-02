package com.estokio.seed;

import com.estokio.config.DatabaseConfig;
import com.estokio.domain.inventory.TipoMovimento;
import com.estokio.domain.order.StatusPedido;
import com.estokio.domain.user.Papel;
import com.estokio.domain.user.Usuario;
import com.estokio.repository.auth.UsuarioRepository;
import com.estokio.security.PasswordEncoder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Popula um banco recem-migrado com dado de demonstracao: 2 lojas, um usuario de
 * cada papel, ~30 SKUs com estoque inicial e ~80 pedidos em estados variados.
 * Nao e uma migration Flyway (Decisao de kickoff #12): dado descartavel de demo nao
 * entra no historico de schema. Roda depois das migrations -- por isso comeca
 * chamando {@link DatabaseConfig#rodarMigrations()}, que e idempotente.
 *
 * <p>Toda quantidade de estoque e movimentada via ledger (nunca um UPDATE direto em
 * {@code quantidade}), e o saldo em {@code estoque_saldo} e atualizado na mesma
 * transacao de cada movimento -- exatamente o padrao que o codigo de producao vai
 * seguir (regra de ouro do projeto).</p>
 */
public final class SeedRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private static final String SENHA_DEV = "senha123";
    private static final List<String> FORMAS_PAGAMENTO = List.of("PIX", "CARTAO_CREDITO", "BOLETO");

    /** Seed fixa: rodar o SeedRunner duas vezes contra bancos diferentes gera o mesmo dado. */
    private final Random aleatorio = new Random(42);
    private final Jdbi jdbi;
    private final UsuarioRepository usuarioRepository;
    private final List<String> credenciais = new ArrayList<>();

    public static void main(String[] args) {
        DatabaseConfig.rodarMigrations();
        Jdbi jdbi = DatabaseConfig.appUserJdbi();
        new SeedRunner(jdbi).executar();
    }

    private SeedRunner(Jdbi jdbi) {
        this.jdbi = jdbi;
        this.usuarioRepository = new UsuarioRepository(jdbi);
    }

    private void executar() {
        log.info("Iniciando seed de demonstracao...");

        Map<String, UUID> planos = jdbi.inTransaction(this::criarPlanos);

        UUID superAdminId = criarUsuarioGlobal("Super Admin Estokio", "superadmin@estokio.dev", Papel.SUPER_ADMIN);
        List<UUID> clientes = criarClientes(10);

        seedarLoja("Loja Aurora", "loja-aurora", planos.get("BASICO"), CatalogoDemo.aurora(), clientes);
        seedarLoja("Loja Cedro", "loja-cedro", planos.get("PRO"), CatalogoDemo.cedro(), clientes);

        log.info("Seed concluido.");
        imprimirCredenciais();
    }

    // ---------------------------------------------------------------- plataforma

    private Map<String, UUID> criarPlanos(Handle handle) {
        Map<String, UUID> ids = new HashMap<>();
        ids.put("BASICO", inserirPlano(handle, "Basico", 50, 200, 3, 0));
        ids.put("PRO", inserirPlano(handle, "Pro", 500, 2000, 10, 9900));
        return ids;
    }

    private UUID inserirPlano(Handle handle, String nome, int maxProdutos, int maxPedidosMes, int maxUsuarios,
                               int precoCentavos) {
        return handle.createQuery("""
                        INSERT INTO plano (id, nome, max_produtos, max_pedidos_mes, max_usuarios, preco_centavos, ativo)
                        VALUES (gen_random_uuid(), :nome, :maxProdutos, :maxPedidosMes, :maxUsuarios, :precoCentavos, true)
                        RETURNING id
                        """)
                .bind("nome", nome)
                .bind("maxProdutos", maxProdutos)
                .bind("maxPedidosMes", maxPedidosMes)
                .bind("maxUsuarios", maxUsuarios)
                .bind("precoCentavos", precoCentavos)
                .mapTo(UUID.class)
                .one();
    }

    private UUID criarUsuarioGlobal(String nome, String email, Papel papel) {
        Usuario usuario = usuarioRepository.inserir(new Usuario(
                UUID.randomUUID(), null, nome, email, PasswordEncoder.hash(SENHA_DEV), papel, true, Instant.now()));
        credenciais.add(papel + ": " + email + " / " + SENHA_DEV);
        return usuario.id();
    }

    private List<UUID> criarClientes(int quantidade) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 1; i <= quantidade; i++) {
            String email = "cliente" + i + "@estokio.dev";
            ids.add(criarUsuarioGlobal("Cliente Demo " + i, email, Papel.CLIENTE));
        }
        return ids;
    }

    // ---------------------------------------------------------------- por loja

    private void seedarLoja(String nome, String slug, UUID planoId, CatalogoDemo catalogo, List<UUID> clientes) {
        UUID tenantId = jdbi.inTransaction(handle -> criarTenant(handle, nome, slug, planoId));

        String adminEmail = "admin@" + slug + ".dev";
        String operadorEmail = "operador@" + slug + ".dev";
        UUID adminId = criarUsuarioTenant("Admin " + nome, adminEmail, Papel.ADMIN_LOJA, tenantId);
        UUID operadorId = criarUsuarioTenant("Operador " + nome, operadorEmail, Papel.OPERADOR, tenantId);

        jdbi.useTransaction(handle -> {
            definirTenantNaSessao(handle, tenantId);
            inserirTenantConfig(handle, tenantId);

            Map<String, UUID> categorias = new HashMap<>();
            for (String categoria : catalogo.categorias()) {
                categorias.put(categoria, inserirCategoria(handle, tenantId, categoria));
            }

            List<VariacaoSeed> variacoes = new ArrayList<>();
            for (ProdutoDemo produto : catalogo.produtos()) {
                UUID categoriaId = categorias.get(produto.categoria());
                UUID produtoId = inserirProduto(handle, tenantId, categoriaId, produto.nome());
                for (VariacaoDemo variacaoDemo : produto.variacoes()) {
                    String sku = catalogo.prefixoSku() + "-" + variacaoDemo.skuSuffix();
                    UUID variacaoId = inserirVariacao(handle, tenantId, produtoId, sku, variacaoDemo.atributos(),
                            produto.precoCentavos());
                    int estoqueInicial = 60 + aleatorio.nextInt(91); // 60..150
                    inserirEstoqueInicial(handle, tenantId, variacaoId, estoqueInicial, adminId);
                    variacoes.add(new VariacaoSeed(variacaoId, produto.nome(), sku, produto.precoCentavos(),
                            new int[]{estoqueInicial, 0}));
                }
            }

            gerarPedidos(handle, tenantId, variacoes, clientes, adminId, operadorId, 40);
        });

        log.info("Loja '{}' seedada: {} SKUs, 40 pedidos.", nome, catalogo.totalVariacoes());
    }

    private UUID criarTenant(Handle handle, String nome, String slug, UUID planoId) {
        return handle.createQuery("""
                        INSERT INTO tenant (id, nome, slug, status, plano_id)
                        VALUES (gen_random_uuid(), :nome, :slug, 'ATIVA', :planoId)
                        RETURNING id
                        """)
                .bind("nome", nome)
                .bind("slug", slug)
                .bind("planoId", planoId)
                .mapTo(UUID.class)
                .one();
    }

    private UUID criarUsuarioTenant(String nome, String email, Papel papel, UUID tenantId) {
        Usuario usuario = usuarioRepository.inserir(new Usuario(
                UUID.randomUUID(), tenantId, nome, email, PasswordEncoder.hash(SENHA_DEV), papel, true, Instant.now()));
        credenciais.add(papel + " (" + tenantId + "): " + email + " / " + SENHA_DEV);
        return usuario.id();
    }

    private void inserirTenantConfig(Handle handle, UUID tenantId) {
        handle.createUpdate("""
                        INSERT INTO tenant_config
                            (tenant_id, frete_fixo_centavos, frete_gratis_acima_centavos, formas_pagamento,
                             minutos_reserva, dias_produto_parado)
                        VALUES (:tenantId, 1500, 20000, CAST(:formasPagamento AS JSONB), 30, 30)
                        """)
                .bind("tenantId", tenantId)
                .bind("formasPagamento", "[\"PIX\", \"CARTAO_CREDITO\", \"BOLETO\"]")
                .execute();
    }

    private UUID inserirCategoria(Handle handle, UUID tenantId, String nome) {
        return handle.createQuery("""
                        INSERT INTO categoria (id, tenant_id, nome, ativo)
                        VALUES (gen_random_uuid(), :tenantId, :nome, true)
                        RETURNING id
                        """)
                .bind("tenantId", tenantId)
                .bind("nome", nome)
                .mapTo(UUID.class)
                .one();
    }

    private UUID inserirProduto(Handle handle, UUID tenantId, UUID categoriaId, String nome) {
        return handle.createQuery("""
                        INSERT INTO produto (id, tenant_id, categoria_id, nome, descricao, ativo)
                        VALUES (gen_random_uuid(), :tenantId, :categoriaId, :nome, :descricao, true)
                        RETURNING id
                        """)
                .bind("tenantId", tenantId)
                .bind("categoriaId", categoriaId)
                .bind("nome", nome)
                .bind("descricao", "Produto de demonstracao gerado pelo SeedRunner.")
                .mapTo(UUID.class)
                .one();
    }

    private UUID inserirVariacao(Handle handle, UUID tenantId, UUID produtoId, String sku,
                                  Map<String, String> atributos, int precoCentavos) {
        return handle.createQuery("""
                        INSERT INTO variacao (id, tenant_id, produto_id, sku, atributos, preco_centavos, ponto_reposicao, ativo)
                        VALUES (gen_random_uuid(), :tenantId, :produtoId, :sku, CAST(:atributos AS JSONB), :precoCentavos, 10, true)
                        RETURNING id
                        """)
                .bind("tenantId", tenantId)
                .bind("produtoId", produtoId)
                .bind("sku", sku)
                .bind("atributos", jsonDeAtributos(atributos))
                .bind("precoCentavos", precoCentavos)
                .mapTo(UUID.class)
                .one();
    }

    private void inserirEstoqueInicial(Handle handle, UUID tenantId, UUID variacaoId, int quantidade, UUID usuarioId) {
        handle.createUpdate("""
                        INSERT INTO estoque_saldo (variacao_id, tenant_id, qtd_fisica, qtd_reservada)
                        VALUES (:variacaoId, :tenantId, 0, 0)
                        """)
                .bind("variacaoId", variacaoId)
                .bind("tenantId", tenantId)
                .execute();

        registrarMovimento(handle, tenantId, variacaoId, TipoMovimento.ENTRADA, quantidade, 0,
                "Estoque inicial de demonstracao", "ESTOQUE_INICIAL", null, usuarioId);
    }

    // ---------------------------------------------------------------- pedidos

    private void gerarPedidos(Handle handle, UUID tenantId, List<VariacaoSeed> variacoes, List<UUID> clientes,
                               UUID adminId, UUID operadorId, int quantidadePedidos) {
        List<StatusPedido> distribuicao = distribuicaoDeStatus(quantidadePedidos);
        Collections.shuffle(distribuicao, aleatorio);

        int numero = 1001;
        for (StatusPedido status : distribuicao) {
            UUID clienteId = clientes.get(aleatorio.nextInt(clientes.size()));
            int quantidadeItens = 1 + aleatorio.nextInt(3);
            List<ItemPedidoSeed> itens = escolherItens(variacoes, quantidadeItens);
            if (itens.isEmpty()) {
                continue; // nenhuma variacao com estoque disponivel sobrou -- pula este pedido
            }

            int subtotal = itens.stream().mapToInt(item -> item.precoCentavos() * item.quantidade()).sum();
            int frete = subtotal >= 20000 ? 0 : 1500;
            int total = subtotal + frete;
            String formaPagamento = FORMAS_PAGAMENTO.get(aleatorio.nextInt(FORMAS_PAGAMENTO.size()));

            Instant criadoEm = instanteAleatorioNoPassado(45);
            Instant reservaExpiraEm = switch (status) {
                case PENDENTE -> criadoEm.plus(30, ChronoUnit.MINUTES);
                case EXPIRADO -> criadoEm.plus(30, ChronoUnit.MINUTES); // no passado: ja venceu
                default -> null;
            };

            UUID pedidoId = inserirPedido(handle, tenantId, clienteId, numero++, status, subtotal, frete, total,
                    formaPagamento, reservaExpiraEm, criadoEm);

            for (ItemPedidoSeed item : itens) {
                inserirPedidoItem(handle, tenantId, pedidoId, item);
                aplicarMovimentosDoItem(handle, tenantId, item, status, pedidoId, adminId);
            }

            inserirHistoricoDeTransicoes(handle, tenantId, pedidoId, status, clienteId, adminId, operadorId, criadoEm);
        }
    }

    private List<StatusPedido> distribuicaoDeStatus(int total) {
        // Pesos somam 40; escalados proporcionalmente para outros tamanhos de lote.
        Map<StatusPedido, Integer> pesos = new LinkedHashMap<>();
        pesos.put(StatusPedido.PENDENTE, 6);
        pesos.put(StatusPedido.CONFIRMADO, 5);
        pesos.put(StatusPedido.SEPARANDO, 4);
        pesos.put(StatusPedido.ENVIADO, 5);
        pesos.put(StatusPedido.ENTREGUE, 12);
        pesos.put(StatusPedido.CANCELADO, 4);
        pesos.put(StatusPedido.EXPIRADO, 2);
        pesos.put(StatusPedido.DEVOLVIDO, 2);

        List<StatusPedido> lista = new ArrayList<>();
        pesos.forEach((status, peso) -> {
            int quantidade = Math.round(peso * total / 40f);
            for (int i = 0; i < quantidade; i++) {
                lista.add(status);
            }
        });
        while (lista.size() < total) {
            lista.add(StatusPedido.ENTREGUE);
        }
        while (lista.size() > total) {
            lista.remove(lista.size() - 1);
        }
        return lista;
    }

    private List<ItemPedidoSeed> escolherItens(List<VariacaoSeed> variacoes, int quantidadeDesejada) {
        List<VariacaoSeed> candidatas = new ArrayList<>(variacoes);
        Collections.shuffle(candidatas, aleatorio);

        List<ItemPedidoSeed> itens = new ArrayList<>();
        for (VariacaoSeed candidata : candidatas) {
            if (itens.size() >= quantidadeDesejada) {
                break;
            }
            int disponivel = candidata.saldoEmMemoria()[0] - candidata.saldoEmMemoria()[1];
            if (disponivel <= 0) {
                continue;
            }
            int quantidade = Math.min(1 + aleatorio.nextInt(3), disponivel);
            itens.add(new ItemPedidoSeed(candidata, quantidade));
        }
        return itens;
    }

    private UUID inserirPedido(Handle handle, UUID tenantId, UUID clienteId, int numero, StatusPedido status,
                                int subtotal, int frete, int total, String formaPagamento, Instant reservaExpiraEm,
                                Instant criadoEm) {
        return handle.createQuery("""
                        INSERT INTO pedido
                            (id, tenant_id, cliente_id, numero, status, subtotal_centavos, frete_centavos,
                             total_centavos, forma_pagamento, endereco, reserva_expira_em, criado_em, atualizado_em)
                        VALUES
                            (gen_random_uuid(), :tenantId, :clienteId, :numero, :status, :subtotal, :frete, :total,
                             :formaPagamento, CAST(:endereco AS JSONB), :reservaExpiraEm, :criadoEm, :criadoEm)
                        RETURNING id
                        """)
                .bind("tenantId", tenantId)
                .bind("clienteId", clienteId)
                .bind("numero", numero)
                .bind("status", status)
                .bind("subtotal", subtotal)
                .bind("frete", frete)
                .bind("total", total)
                .bind("formaPagamento", formaPagamento)
                .bind("endereco", enderecoJson())
                .bind("reservaExpiraEm", reservaExpiraEm)
                .bind("criadoEm", criadoEm)
                .mapTo(UUID.class)
                .one();
    }
    // idempotency_key fica NULL de proposito: pedidos de seed nao passam pelo fluxo
    // HTTP de POST /pedidos, entao nao existe uma chave de idempotencia real associada.

    private void inserirPedidoItem(Handle handle, UUID tenantId, UUID pedidoId, ItemPedidoSeed item) {
        handle.createUpdate("""
                        INSERT INTO pedido_item
                            (id, tenant_id, pedido_id, variacao_id, quantidade, preco_unit_centavos, nome_snapshot, sku_snapshot)
                        VALUES (gen_random_uuid(), :tenantId, :pedidoId, :variacaoId, :quantidade, :precoUnit, :nome, :sku)
                        """)
                .bind("tenantId", tenantId)
                .bind("pedidoId", pedidoId)
                .bind("variacaoId", item.variacao().id())
                .bind("quantidade", item.quantidade())
                .bind("precoUnit", item.precoCentavos())
                .bind("nome", item.variacao().nomeProduto())
                .bind("sku", item.variacao().sku())
                .execute();
    }

    /** Deltas de ledger consistentes com a tabela da secao 4.1 e a maquina de estados (secao 6). */
    private void aplicarMovimentosDoItem(Handle handle, UUID tenantId, ItemPedidoSeed item, StatusPedido status,
                                          UUID pedidoId, UUID usuarioId) {
        UUID variacaoId = item.variacao().id();
        int[] saldoEmMemoria = item.variacao().saldoEmMemoria();
        int qtd = item.quantidade();

        registrarMovimento(handle, tenantId, variacaoId, TipoMovimento.RESERVA, 0, qtd,
                "Reserva do pedido", "PEDIDO", pedidoId, usuarioId, saldoEmMemoria);

        switch (status) {
            case PENDENTE, CONFIRMADO, SEPARANDO -> {
                // reserva ainda ativa, nenhum movimento adicional
            }
            case ENVIADO, ENTREGUE -> registrarMovimento(handle, tenantId, variacaoId, TipoMovimento.BAIXA,
                    -qtd, -qtd, "Baixa fisica no envio", "PEDIDO", pedidoId, usuarioId, saldoEmMemoria);
            case CANCELADO -> registrarMovimento(handle, tenantId, variacaoId, TipoMovimento.LIBERACAO,
                    0, -qtd, "Cancelamento do pedido", "PEDIDO", pedidoId, usuarioId, saldoEmMemoria);
            case EXPIRADO -> registrarMovimento(handle, tenantId, variacaoId, TipoMovimento.LIBERACAO,
                    0, -qtd, "Reserva expirada automaticamente", "PEDIDO", pedidoId, usuarioId, saldoEmMemoria);
            case DEVOLVIDO -> {
                registrarMovimento(handle, tenantId, variacaoId, TipoMovimento.BAIXA, -qtd, -qtd,
                        "Baixa fisica no envio", "PEDIDO", pedidoId, usuarioId, saldoEmMemoria);
                registrarMovimento(handle, tenantId, variacaoId, TipoMovimento.DEVOLUCAO, qtd, 0,
                        "Devolucao do cliente", "PEDIDO", pedidoId, usuarioId, saldoEmMemoria);
            }
        }
    }

    private void registrarMovimento(Handle handle, UUID tenantId, UUID variacaoId, TipoMovimento tipo,
                                     int deltaFisico, int deltaReservado, String motivo, String origemTipo,
                                     UUID origemId, UUID usuarioId) {
        registrarMovimento(handle, tenantId, variacaoId, tipo, deltaFisico, deltaReservado, motivo, origemTipo,
                origemId, usuarioId, null);
    }

    /**
     * @param saldoEmMemoria espelho em memoria de [qtd_fisica, qtd_reservada] (ver {@link VariacaoSeed}),
     *                       atualizado junto com o UPDATE em estoque_saldo; {@code null} quando ainda nao
     *                       existe (usado so pelo estoque inicial, cujo espelho e criado logo em seguida).
     */
    private void registrarMovimento(Handle handle, UUID tenantId, UUID variacaoId, TipoMovimento tipo,
                                     int deltaFisico, int deltaReservado, String motivo, String origemTipo,
                                     UUID origemId, UUID usuarioId, int[] saldoEmMemoria) {
        handle.createUpdate("""
                        INSERT INTO estoque_movimento
                            (tenant_id, variacao_id, tipo, delta_fisico, delta_reservado, motivo, origem_tipo, origem_id, usuario_id)
                        VALUES (:tenantId, :variacaoId, :tipo, :deltaFisico, :deltaReservado, :motivo, :origemTipo, :origemId, :usuarioId)
                        """)
                .bind("tenantId", tenantId)
                .bind("variacaoId", variacaoId)
                .bind("tipo", tipo)
                .bind("deltaFisico", deltaFisico)
                .bind("deltaReservado", deltaReservado)
                .bind("motivo", motivo)
                .bind("origemTipo", origemTipo)
                .bind("origemId", origemId)
                .bind("usuarioId", usuarioId)
                .execute();

        handle.createUpdate("""
                        UPDATE estoque_saldo
                        SET qtd_fisica = qtd_fisica + :deltaFisico,
                            qtd_reservada = qtd_reservada + :deltaReservado,
                            atualizado_em = now()
                        WHERE variacao_id = :variacaoId
                        """)
                .bind("deltaFisico", deltaFisico)
                .bind("deltaReservado", deltaReservado)
                .bind("variacaoId", variacaoId)
                .execute();

        if (saldoEmMemoria != null) {
            saldoEmMemoria[0] += deltaFisico;
            saldoEmMemoria[1] += deltaReservado;
        }
    }

    private void inserirHistoricoDeTransicoes(Handle handle, UUID tenantId, UUID pedidoId, StatusPedido statusFinal,
                                               UUID clienteId, UUID adminId, UUID operadorId, Instant criadoEm) {
        List<Transicao> transicoes = new ArrayList<>();
        transicoes.add(new Transicao(null, StatusPedido.PENDENTE, clienteId, null));

        switch (statusFinal) {
            case PENDENTE -> {
            }
            case CONFIRMADO -> transicoes.add(new Transicao(StatusPedido.PENDENTE, StatusPedido.CONFIRMADO, adminId, null));
            case SEPARANDO -> {
                transicoes.add(new Transicao(StatusPedido.PENDENTE, StatusPedido.CONFIRMADO, adminId, null));
                transicoes.add(new Transicao(StatusPedido.CONFIRMADO, StatusPedido.SEPARANDO, operadorId, null));
            }
            case ENVIADO -> {
                transicoes.add(new Transicao(StatusPedido.PENDENTE, StatusPedido.CONFIRMADO, adminId, null));
                transicoes.add(new Transicao(StatusPedido.CONFIRMADO, StatusPedido.SEPARANDO, operadorId, null));
                transicoes.add(new Transicao(StatusPedido.SEPARANDO, StatusPedido.ENVIADO, operadorId, null));
            }
            case ENTREGUE -> {
                transicoes.add(new Transicao(StatusPedido.PENDENTE, StatusPedido.CONFIRMADO, adminId, null));
                transicoes.add(new Transicao(StatusPedido.CONFIRMADO, StatusPedido.SEPARANDO, operadorId, null));
                transicoes.add(new Transicao(StatusPedido.SEPARANDO, StatusPedido.ENVIADO, operadorId, null));
                transicoes.add(new Transicao(StatusPedido.ENVIADO, StatusPedido.ENTREGUE, null, null));
            }
            case CANCELADO -> transicoes.add(new Transicao(StatusPedido.PENDENTE, StatusPedido.CANCELADO, clienteId,
                    "Cliente desistiu da compra"));
            case EXPIRADO -> transicoes.add(new Transicao(StatusPedido.PENDENTE, StatusPedido.EXPIRADO, null,
                    "Reserva expirada automaticamente"));
            case DEVOLVIDO -> {
                transicoes.add(new Transicao(StatusPedido.PENDENTE, StatusPedido.CONFIRMADO, adminId, null));
                transicoes.add(new Transicao(StatusPedido.CONFIRMADO, StatusPedido.SEPARANDO, operadorId, null));
                transicoes.add(new Transicao(StatusPedido.SEPARANDO, StatusPedido.ENVIADO, operadorId, null));
                transicoes.add(new Transicao(StatusPedido.ENVIADO, StatusPedido.ENTREGUE, null, null));
                transicoes.add(new Transicao(StatusPedido.ENTREGUE, StatusPedido.DEVOLVIDO, clienteId,
                        "Produto com defeito"));
            }
        }

        Instant instanteTransicao = criadoEm;
        for (Transicao transicao : transicoes) {
            handle.createUpdate("""
                            INSERT INTO pedido_status_historico
                                (id, tenant_id, pedido_id, status_de, status_para, usuario_id, motivo, criado_em)
                            VALUES (gen_random_uuid(), :tenantId, :pedidoId, :statusDe, :statusPara, :usuarioId, :motivo, :criadoEm)
                            """)
                    .bind("tenantId", tenantId)
                    .bind("pedidoId", pedidoId)
                    .bind("statusDe", transicao.de())
                    .bind("statusPara", transicao.para())
                    .bind("usuarioId", transicao.usuarioId())
                    .bind("motivo", transicao.motivo())
                    .bind("criadoEm", instanteTransicao)
                    .execute();
            instanteTransicao = instanteTransicao.plus(1, ChronoUnit.HOURS);
        }
    }

    // ---------------------------------------------------------------- utilidades

    private void definirTenantNaSessao(Handle handle, UUID tenantId) {
        // Mesmo mecanismo de com.estokio.security.TenantContext#aplicarNaTransacao,
        // mas sem depender do ThreadLocal (SeedRunner nao roda dentro de uma requisicao HTTP).
        handle.execute("SELECT set_config('app.tenant_id', ?, true)", tenantId.toString());
    }

    private Instant instanteAleatorioNoPassado(int maxDiasAtras) {
        long segundosAtras = (long) aleatorio.nextInt(maxDiasAtras * 24 * 60 * 60);
        return Instant.now().minusSeconds(segundosAtras);
    }

    private String enderecoJson() {
        return """
                {"logradouro": "Rua das Demonstracoes, 123", "cidade": "Sao Paulo", "uf": "SP", "cep": "01000-000"}""";
    }

    private String jsonDeAtributos(Map<String, String> atributos) {
        StringBuilder json = new StringBuilder("{");
        boolean primeiro = true;
        for (Map.Entry<String, String> atributo : atributos.entrySet()) {
            if (!primeiro) {
                json.append(", ");
            }
            json.append('"').append(atributo.getKey()).append("\": \"").append(atributo.getValue()).append('"');
            primeiro = false;
        }
        return json.append('}').toString();
    }

    private void imprimirCredenciais() {
        log.info("==================== Logins de demonstracao (senha: {}) ====================", SENHA_DEV);
        credenciais.forEach(log::info);
        log.info("===============================================================================");
    }

    private record Transicao(StatusPedido de, StatusPedido para, UUID usuarioId, String motivo) {
    }

    private record ItemPedidoSeed(VariacaoSeed variacao, int quantidade) {
        int precoCentavos() {
            return variacao.precoCentavos();
        }
    }

    /** {@code saldoEmMemoria} espelha [qtd_fisica, qtd_reservada] em tempo real, evitando violar as CHECKs. */
    private record VariacaoSeed(UUID id, String nomeProduto, String sku, int precoCentavos, int[] saldoEmMemoria) {
    }

    private record VariacaoDemo(String skuSuffix, Map<String, String> atributos) {
    }

    private record ProdutoDemo(String nome, String categoria, int precoCentavos, List<VariacaoDemo> variacoes) {
    }

    private record CatalogoDemo(String prefixoSku, List<String> categorias, List<ProdutoDemo> produtos) {

        int totalVariacoes() {
            return produtos.stream().mapToInt(p -> p.variacoes().size()).sum();
        }

        static CatalogoDemo aurora() {
            return new CatalogoDemo("AUR", List.of("Vestuario", "Calcados", "Acessorios"), List.of(
                    new ProdutoDemo("Camiseta Basica", "Vestuario", 3990, List.of(
                            new VariacaoDemo("CAM-P-BRANCO", Map.of("tamanho", "P", "cor", "Branco")),
                            new VariacaoDemo("CAM-M-BRANCO", Map.of("tamanho", "M", "cor", "Branco")),
                            new VariacaoDemo("CAM-G-BRANCO", Map.of("tamanho", "G", "cor", "Branco")),
                            new VariacaoDemo("CAM-M-PRETO", Map.of("tamanho", "M", "cor", "Preto")))),
                    new ProdutoDemo("Moletom Capuz", "Vestuario", 12990, List.of(
                            new VariacaoDemo("MOL-M-CINZA", Map.of("tamanho", "M", "cor", "Cinza")),
                            new VariacaoDemo("MOL-G-CINZA", Map.of("tamanho", "G", "cor", "Cinza")))),
                    new ProdutoDemo("Calca Jeans", "Vestuario", 15990, List.of(
                            new VariacaoDemo("CAL-38-AZUL", Map.of("tamanho", "38", "cor", "Azul")),
                            new VariacaoDemo("CAL-40-AZUL", Map.of("tamanho", "40", "cor", "Azul")),
                            new VariacaoDemo("CAL-42-AZUL", Map.of("tamanho", "42", "cor", "Azul")))),
                    new ProdutoDemo("Tenis Casual", "Calcados", 19990, List.of(
                            new VariacaoDemo("TEN-39-PRETO", Map.of("tamanho", "39", "cor", "Preto")),
                            new VariacaoDemo("TEN-40-PRETO", Map.of("tamanho", "40", "cor", "Preto")),
                            new VariacaoDemo("TEN-41-PRETO", Map.of("tamanho", "41", "cor", "Preto")))),
                    new ProdutoDemo("Bone Aba Reta", "Acessorios", 4990, List.of(
                            new VariacaoDemo("BON-UN-PRETO", Map.of("tamanho", "Unico", "cor", "Preto")),
                            new VariacaoDemo("BON-UN-BRANCO", Map.of("tamanho", "Unico", "cor", "Branco")))),
                    new ProdutoDemo("Mochila Urbana", "Acessorios", 8990, List.of(
                            new VariacaoDemo("MOC-UN-PRETO", Map.of("tamanho", "Unico", "cor", "Preto"))))));
        }

        static CatalogoDemo cedro() {
            return new CatalogoDemo("CED", List.of("Casa", "Cozinha", "Decoracao"), List.of(
                    new ProdutoDemo("Caneca Ceramica", "Cozinha", 2990, List.of(
                            new VariacaoDemo("CAN-300-BRANCA", Map.of("capacidade", "300ml", "cor", "Branca")),
                            new VariacaoDemo("CAN-300-PRETA", Map.of("capacidade", "300ml", "cor", "Preta")),
                            new VariacaoDemo("CAN-450-BRANCA", Map.of("capacidade", "450ml", "cor", "Branca")))),
                    new ProdutoDemo("Jogo de Panelas", "Cozinha", 29990, List.of(
                            new VariacaoDemo("PAN-3PC-INOX", Map.of("pecas", "3", "material", "Inox")))),
                    new ProdutoDemo("Luminaria de Mesa", "Decoracao", 9990, List.of(
                            new VariacaoDemo("LUM-P-BEGE", Map.of("tamanho", "Pequena", "cor", "Bege")),
                            new VariacaoDemo("LUM-G-BEGE", Map.of("tamanho", "Grande", "cor", "Bege")))),
                    new ProdutoDemo("Vaso Decorativo", "Decoracao", 6990, List.of(
                            new VariacaoDemo("VAS-P-TERRACOTA", Map.of("tamanho", "Pequeno", "cor", "Terracota")),
                            new VariacaoDemo("VAS-M-TERRACOTA", Map.of("tamanho", "Medio", "cor", "Terracota")),
                            new VariacaoDemo("VAS-G-TERRACOTA", Map.of("tamanho", "Grande", "cor", "Terracota")))),
                    new ProdutoDemo("Tapete Sala", "Casa", 24990, List.of(
                            new VariacaoDemo("TAP-1X15-CINZA", Map.of("tamanho", "1x1,5m", "cor", "Cinza")),
                            new VariacaoDemo("TAP-2X25-CINZA", Map.of("tamanho", "2x2,5m", "cor", "Cinza")))),
                    new ProdutoDemo("Kit Toalhas Banho", "Casa", 8990, List.of(
                            new VariacaoDemo("TOA-2PC-BRANCO", Map.of("pecas", "2", "cor", "Branco")),
                            new VariacaoDemo("TOA-2PC-AZUL", Map.of("pecas", "2", "cor", "Azul")),
                            new VariacaoDemo("TOA-4PC-BRANCO", Map.of("pecas", "4", "cor", "Branco")),
                            new VariacaoDemo("TOA-4PC-AZUL", Map.of("pecas", "4", "cor", "Azul"))))));
        }
    }
}

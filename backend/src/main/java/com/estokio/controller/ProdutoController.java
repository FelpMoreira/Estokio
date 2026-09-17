package com.estokio.controller;

import com.estokio.domain.catalog.Produto;
import com.estokio.domain.catalog.Variacao;
import com.estokio.exception.ApiException;
import com.estokio.security.PapelRole;
import com.estokio.service.catalog.ProdutoService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;
import java.util.UUID;

/**
 * Cadastro de catalogo do painel da loja (Fase 1, item 2 -- ver [[Fase 1 - MVP]]).
 * Registrado sobre o prefixo {@code /api/loja/*} (ver {@code Main}), que ja passa por
 * {@link com.estokio.security.TenantMiddleware} + {@link com.estokio.security.RoleMiddleware}.
 * Cada rota tambem declara {@link PapelRole#ADMIN_LOJA}/{@link PapelRole#OPERADOR}
 * explicitamente por clareza/defesa em profundidade.
 */
public final class ProdutoController {

    private static final int SKU_MAX_LENGTH = 60;
    private static final int NOME_PRODUTO_MAX_LENGTH = 200;

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    public void registrar(Javalin app) {
        app.post("/api/loja/produtos", this::criarProduto, PapelRole.ADMIN_LOJA, PapelRole.OPERADOR);
        app.get("/api/loja/produtos", this::listarProdutos, PapelRole.ADMIN_LOJA, PapelRole.OPERADOR);
        app.post("/api/loja/produtos/{id}/variacoes", this::criarVariacao, PapelRole.ADMIN_LOJA, PapelRole.OPERADOR);
        app.patch("/api/loja/variacoes/{id}", this::atualizarVariacao, PapelRole.ADMIN_LOJA, PapelRole.OPERADOR);
    }

    private void criarProduto(Context ctx) {
        CriarProdutoRequest corpo = ctx.bodyValidator(CriarProdutoRequest.class)
                .check(r -> !isEmBranco(r.nome()), "nome e obrigatorio")
                .check(r -> r.nome() == null || r.nome().trim().length() <= NOME_PRODUTO_MAX_LENGTH,
                        "nome deve ter no maximo " + NOME_PRODUTO_MAX_LENGTH + " caracteres")
                .get();

        Produto produto = produtoService.criarProduto(
                corpo.nome().trim(),
                corpo.descricao() == null ? null : corpo.descricao().trim());

        ctx.status(201).json(produto);
    }

    private void listarProdutos(Context ctx) {
        ctx.json(produtoService.listarProdutos());
    }

    private void criarVariacao(Context ctx) {
        UUID produtoId = uuidPathParam(ctx, "id", "PRODUTO_NAO_ENCONTRADO", "Produto nao encontrado.");

        CriarVariacaoRequest corpo = ctx.bodyValidator(CriarVariacaoRequest.class)
                .check(r -> !isEmBranco(r.sku()), "sku e obrigatorio")
                .check(r -> r.sku() == null || r.sku().trim().length() <= SKU_MAX_LENGTH,
                        "sku deve ter no maximo " + SKU_MAX_LENGTH + " caracteres")
                .check(r -> r.precoCentavos() != null && r.precoCentavos() >= 0,
                        "precoCentavos e obrigatorio e deve ser maior ou igual a zero")
                .check(r -> r.pontoReposicao() == null || r.pontoReposicao() >= 0,
                        "pontoReposicao deve ser maior ou igual a zero")
                .get();

        Variacao variacao = produtoService.criarVariacao(
                produtoId,
                corpo.sku().trim(),
                corpo.atributos() == null ? Map.of() : corpo.atributos(),
                corpo.precoCentavos(),
                corpo.pontoReposicao() == null ? 0 : corpo.pontoReposicao());

        ctx.status(201).json(variacao);
    }

    private void atualizarVariacao(Context ctx) {
        UUID id = uuidPathParam(ctx, "id", "VARIACAO_NAO_ENCONTRADA", "Variacao nao encontrada.");

        AtualizarVariacaoRequest corpo = ctx.bodyValidator(AtualizarVariacaoRequest.class)
                .check(r -> r.precoCentavos() == null || r.precoCentavos() >= 0,
                        "precoCentavos deve ser maior ou igual a zero")
                .check(r -> r.pontoReposicao() == null || r.pontoReposicao() >= 0,
                        "pontoReposicao deve ser maior ou igual a zero")
                .get();

        Variacao variacao = produtoService.atualizarVariacao(
                id, corpo.precoCentavos(), corpo.pontoReposicao(), corpo.ativo());

        ctx.json(variacao);
    }

    private static boolean isEmBranco(String valor) {
        return valor == null || valor.isBlank();
    }

    /**
     * Javalin 6 nao tem um conversor de {@link UUID} registrado por padrao para
     * {@code ctx.pathParamAsClass} (lança {@code MissingConverterException}), entao o
     * parse e manual aqui. Um formato invalido de UUID no path e tratado como "nao
     * encontrado" -- do ponto de vista do cliente da API, um id que nao e nem um UUID
     * valido nunca vai corresponder a um registro real deste tenant.
     */
    private static UUID uuidPathParam(Context ctx, String nome, String codigoErro, String mensagem) {
        try {
            return UUID.fromString(ctx.pathParam(nome));
        } catch (IllegalArgumentException e) {
            throw ApiException.naoEncontrado(codigoErro, mensagem);
        }
    }

    private record CriarProdutoRequest(String nome, String descricao) {
    }

    private record CriarVariacaoRequest(String sku, Map<String, String> atributos, Integer precoCentavos,
                                         Integer pontoReposicao) {
    }

    private record AtualizarVariacaoRequest(Integer precoCentavos, Integer pontoReposicao, Boolean ativo) {
    }
}

package com.estokio.controller;

import com.estokio.domain.inventory.EstoqueSaldoResumo;
import com.estokio.security.PapelRole;
import com.estokio.service.inventory.EstoqueService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.UUID;

/**
 * Movimentacao/consulta de estoque do painel da loja (Fase 1, item 2 -- ver
 * [[Fase 1 - MVP]]). Registrado sobre o prefixo {@code /api/loja/*} (ver {@code Main}).
 * Fora de escopo aqui (Fase 2, ver a tarefa): ajuste manual com estorno e extrato de
 * movimentos por SKU.
 */
public final class EstoqueController {

    private final EstoqueService estoqueService;

    public EstoqueController(EstoqueService estoqueService) {
        this.estoqueService = estoqueService;
    }

    public void registrar(Javalin app) {
        app.post("/api/loja/estoque/entrada", this::registrarEntrada, PapelRole.ADMIN_LOJA, PapelRole.OPERADOR);
        app.get("/api/loja/estoque", this::listarSaldos, PapelRole.ADMIN_LOJA, PapelRole.OPERADOR);
    }

    private void registrarEntrada(Context ctx) {
        EntradaEstoqueRequest corpo = ctx.bodyValidator(EntradaEstoqueRequest.class)
                .check(r -> r.variacaoId() != null, "variacaoId e obrigatorio")
                .check(r -> r.quantidade() != null, "quantidade e obrigatoria")
                .get();

        EstoqueSaldoResumo resumo = estoqueService.registrarEntrada(
                corpo.variacaoId(),
                corpo.quantidade(),
                corpo.motivo() == null || corpo.motivo().isBlank() ? null : corpo.motivo().trim());

        ctx.status(201).json(resumo);
    }

    private void listarSaldos(Context ctx) {
        ctx.json(estoqueService.listarSaldos());
    }

    private record EntradaEstoqueRequest(UUID variacaoId, Integer quantidade, String motivo) {
    }
}

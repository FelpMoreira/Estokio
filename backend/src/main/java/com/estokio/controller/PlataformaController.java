package com.estokio.controller;

import com.estokio.domain.tenant.Tenant;
import com.estokio.security.PapelRole;
import com.estokio.service.tenant.TenantService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.UUID;

/**
 * Rotas exclusivas do Super Admin (Fase 1, item 1 -- ver Fase 1 - MVP e Papeis e Permissoes).
 * Passam por {@link com.estokio.security.TenantMiddleware} + {@link com.estokio.security.RoleMiddleware}
 * (registrados em Main sobre o prefixo {@code /api/plataforma/*}), e cada rota tambem declara
 * {@link PapelRole#SUPER_ADMIN} explicitamente por clareza/defesa em profundidade.
 */
public final class PlataformaController {

    private final TenantService tenantService;

    public PlataformaController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public void registrar(Javalin app) {
        app.get("/api/plataforma/planos", this::listarPlanos, PapelRole.SUPER_ADMIN);
        app.get("/api/plataforma/lojas", this::listarLojas, PapelRole.SUPER_ADMIN);
        app.post("/api/plataforma/lojas", this::criarLoja, PapelRole.SUPER_ADMIN);
    }

    private void listarPlanos(Context ctx) {
        ctx.json(tenantService.listarPlanosAtivos());
    }

    private void listarLojas(Context ctx) {
        ctx.json(tenantService.listarLojas());
    }

    private void criarLoja(Context ctx) {
        CriarLojaRequest corpo = ctx.bodyValidator(CriarLojaRequest.class)
                .check(r -> r.loja() != null, "loja e obrigatoria")
                .check(r -> r.admin() != null, "admin e obrigatorio")
                .check(r -> r.loja() != null && !isEmBranco(r.loja().nome()), "loja.nome e obrigatorio")
                .check(r -> r.loja() != null && !isEmBranco(r.loja().slug()), "loja.slug e obrigatorio")
                .check(r -> r.loja() != null && r.loja().planoId() != null, "loja.planoId e obrigatorio")
                .check(r -> r.admin() != null && !isEmBranco(r.admin().nome()), "admin.nome e obrigatorio")
                .check(r -> r.admin() != null && !isEmBranco(r.admin().email()), "admin.email e obrigatorio")
                .check(r -> r.admin() != null && r.admin().senha() != null && r.admin().senha().length() >= 8,
                        "admin.senha deve ter ao menos 8 caracteres")
                .get();

        Tenant tenant = tenantService.criarLoja(
                corpo.loja().nome().trim(),
                corpo.loja().slug().trim().toLowerCase(),
                corpo.loja().planoId(),
                corpo.admin().nome().trim(),
                corpo.admin().email().trim().toLowerCase(),
                corpo.admin().senha());

        ctx.status(201).json(tenant);
    }

    private static boolean isEmBranco(String valor) {
        return valor == null || valor.isBlank();
    }

    private record LojaRequest(String nome, String slug, UUID planoId) {
    }

    private record AdminRequest(String nome, String email, String senha) {
    }

    private record CriarLojaRequest(LojaRequest loja, AdminRequest admin) {
    }
}

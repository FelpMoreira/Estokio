package com.estokio.config;

import io.javalin.config.JavalinConfig;

/**
 * CORS de desenvolvimento: qualquer origem, sem cookies (autenticacao e via header
 * {@code Authorization: Bearer}, nunca cookie de sessao, entao nao ha risco de CSRF
 * nem necessidade de {@code allowCredentials}). Restringir a origem do frontend em
 * producao fica registrado como pendencia (ver Notas de Implementacao - Fase 0).
 */
public final class CorsConfig {

    private CorsConfig() {
    }

    public static void aplicar(JavalinConfig config) {
        config.bundledPlugins.enableCors(cors -> cors.addRule(regra -> regra.anyHost()));
    }
}

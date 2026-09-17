package com.estokio.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

/**
 * Monta a instancia do Javalin (nao inicia o servidor -- isso e responsabilidade do
 * {@code Main}). Configura o mapper JSON (com suporte a java.time e naming snake_case,
 * para bater com os payloads da spec, ex: {@code access_token}/{@code refresh_token}) e CORS.
 */
public final class JavalinConfig {

    private JavalinConfig() {
    }

    public static Javalin criar() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                // Sem isso, Instant serializa como epoch numerico (ex: 1789644561.65) em vez de
                // ISO-8601 -- so ficou visivel na Fase 1 porque Tenant.criadoEm e o primeiro
                // Instant exposto num corpo de resposta JSON (Fase 0 so tinha strings/booleans/enums).
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jsonMapper(new JavalinJackson(mapper, false));
            CorsConfig.aplicar(config);
        });
    }
}

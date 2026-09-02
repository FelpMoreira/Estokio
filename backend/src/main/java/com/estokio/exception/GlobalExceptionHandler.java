package com.estokio.exception;

import io.javalin.Javalin;
import io.javalin.http.HttpResponseException;
import io.javalin.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Handler global de excecao (ver secao 11 da spec / Fase 0 no vault): garante que
 * toda resposta de erro da API, independente da causa, siga o contrato {@link ErroPadrao}.
 */
public final class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private GlobalExceptionHandler() {
    }

    public static void registrar(Javalin app) {
        app.exception(ApiException.class, (e, ctx) ->
                ctx.status(e.status()).json(e.paraErroPadrao()));

        // ctx.bodyValidator(...) lanca ValidationException; normalizamos para o contrato
        // do projeto em vez do formato default do Javalin.
        app.exception(ValidationException.class, (e, ctx) -> {
            List<Map<String, Object>> detalhes = e.getErrors().entrySet().stream()
                    .map(entry -> Map.<String, Object>of(
                            "campo", entry.getKey(),
                            "mensagens", entry.getValue().stream().map(err -> err.getMessage()).toList()))
                    .toList();
            ctx.status(400).json(new ErroPadrao("VALIDACAO", "Dados invalidos.", detalhes));
        });

        // Demais excecoes internas do Javalin (BadRequestResponse, NotFoundResponse, etc.)
        // tem seu proprio formato de JSON por padrao; normalizamos para o contrato do projeto.
        app.exception(HttpResponseException.class, (e, ctx) ->
                ctx.status(e.getStatus()).json(new ErroPadrao("ERRO_HTTP", e.getMessage(), List.of())));

        app.exception(Exception.class, (e, ctx) -> {
            log.error("Erro nao tratado em {} {}", ctx.method(), ctx.path(), e);
            ctx.status(500).json(new ErroPadrao("ERRO_INTERNO", "Ocorreu um erro inesperado.", List.of()));
        });
    }
}

package com.estokio.exception;

import java.util.List;

/**
 * Contrato de erro padrao de toda a API (ver secao 11 da spec do projeto).
 * Toda resposta de erro tem exatamente este formato, nunca a pilha de exception
 * crua do Javalin.
 */
public record ErroPadrao(String codigo, String mensagem, List<?> detalhes) {

    public ErroPadrao(String codigo, String mensagem) {
        this(codigo, mensagem, List.of());
    }
}

package com.estokio.exception;

import java.util.List;

/**
 * Excecao de negocio/validacao com status HTTP e codigo de erro explicitos.
 * O {@link com.estokio.exception.GlobalExceptionHandler} converte qualquer instancia
 * desta classe direto para o contrato {@link ErroPadrao}, sem vazar detalhe de
 * implementacao (stack trace, mensagem de driver JDBC, etc).
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String codigo;
    private final List<?> detalhes;

    public ApiException(int status, String codigo, String mensagem) {
        this(status, codigo, mensagem, List.of());
    }

    public ApiException(int status, String codigo, String mensagem, List<?> detalhes) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
        this.detalhes = detalhes;
    }

    public static ApiException naoEncontrado(String codigo, String mensagem) {
        return new ApiException(404, codigo, mensagem);
    }

    public static ApiException conflito(String codigo, String mensagem) {
        return new ApiException(409, codigo, mensagem);
    }

    public static ApiException naoAutorizado(String mensagem) {
        return new ApiException(401, "NAO_AUTORIZADO", mensagem);
    }

    public static ApiException acessoNegado(String mensagem) {
        return new ApiException(403, "ACESSO_NEGADO", mensagem);
    }

    public static ApiException requisicaoInvalida(String codigo, String mensagem) {
        return new ApiException(400, codigo, mensagem);
    }

    public int status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }

    public List<?> detalhes() {
        return detalhes;
    }

    public ErroPadrao paraErroPadrao() {
        return new ErroPadrao(codigo, getMessage(), detalhes);
    }
}

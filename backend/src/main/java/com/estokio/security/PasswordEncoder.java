package com.estokio.security;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Wrapper fino sobre jBCrypt. Nenhum outro ponto do codigo deve chamar
 * {@link BCrypt} diretamente -- centraliza o custo do algoritmo aqui.
 */
public final class PasswordEncoder {

    /** Fator de custo do BCrypt. 12 e um valor comum de producao (~250ms por hash em hardware atual). */
    private static final int WORKLOAD = 12;

    private PasswordEncoder() {
    }

    public static String hash(String senhaPura) {
        return BCrypt.hashpw(senhaPura, BCrypt.gensalt(WORKLOAD));
    }

    public static boolean confere(String senhaPura, String hash) {
        return BCrypt.checkpw(senhaPura, hash);
    }
}

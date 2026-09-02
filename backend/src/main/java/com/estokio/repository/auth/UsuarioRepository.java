package com.estokio.repository.auth;

import com.estokio.domain.user.Usuario;
import org.jdbi.v3.core.Jdbi;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code usuario} NAO tem RLS (ver Multi-Tenancy e RLS): o login precisa localizar o
 * usuario por email antes mesmo de saber o tenant, e SUPER_ADMIN/CLIENTE sao globais
 * (tenant_id NULL). Toda query aqui e "sem filtro de tenant" por design, nao por descuido.
 */
public final class UsuarioRepository {

    private static final String COLUNAS = "id, tenant_id, nome, email, senha_hash, papel, ativo, criado_em";

    private final Jdbi jdbi;

    public UsuarioRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT " + COLUNAS + " FROM usuario WHERE email = :email")
                .bind("email", email)
                .mapTo(Usuario.class)
                .findOne());
    }

    public Optional<Usuario> buscarPorId(UUID id) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT " + COLUNAS + " FROM usuario WHERE id = :id")
                .bind("id", id)
                .mapTo(Usuario.class)
                .findOne());
    }

    /** Usado pelo SeedRunner e por futuros fluxos de cadastro (convite de operador, registro de cliente). */
    public Usuario inserir(Usuario usuario) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "INSERT INTO usuario (" + COLUNAS + ") "
                                + "VALUES (:id, :tenantId, :nome, :email, :senhaHash, :papel, :ativo, :criadoEm) "
                                + "RETURNING " + COLUNAS)
                .bindMethods(usuario)
                .mapTo(Usuario.class)
                .one());
    }
}

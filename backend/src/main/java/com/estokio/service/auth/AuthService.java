package com.estokio.service.auth;

import com.estokio.domain.user.RefreshToken;
import com.estokio.domain.user.Usuario;
import com.estokio.exception.ApiException;
import com.estokio.repository.auth.RefreshTokenRepository;
import com.estokio.repository.auth.UsuarioRepository;
import com.estokio.security.JwtService;
import com.estokio.security.PasswordEncoder;

import java.time.Instant;

/**
 * Login (email + senha via BCrypt) e refresh (rotaciona o refresh token persistido)
 * -- as duas unicas regras de negocio da Fase 0, para provar JWT + BCrypt + contrato
 * de erro funcionando ponta a ponta.
 */
public final class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, RefreshTokenRepository refreshTokenRepository,
                        JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    public TokenPair login(String email, String senha) {
        Usuario usuario = usuarioRepository.buscarPorEmail(email)
                .filter(Usuario::ativo)
                .orElseThrow(() -> ApiException.naoAutorizado("Email ou senha invalidos."));

        if (!PasswordEncoder.confere(senha, usuario.senhaHash())) {
            throw ApiException.naoAutorizado("Email ou senha invalidos.");
        }

        return emitirPar(usuario);
    }

    public TokenPair refresh(String refreshTokenBruto) {
        String hash = jwtService.hashRefreshToken(refreshTokenBruto);
        RefreshToken tokenPersistido = refreshTokenRepository.buscarPorHash(hash)
                .orElseThrow(() -> ApiException.naoAutorizado("Refresh token invalido."));

        if (!tokenPersistido.valido(Instant.now())) {
            throw ApiException.naoAutorizado("Refresh token expirado ou revogado.");
        }

        Usuario usuario = usuarioRepository.buscarPorId(tokenPersistido.usuarioId())
                .filter(Usuario::ativo)
                .orElseThrow(() -> ApiException.naoAutorizado("Usuario nao encontrado ou inativo."));

        // Rotaciona: revoga o token usado e emite um par novo. Reduz a janela de reuso
        // se o refresh token vazar (um retry com o token antigo passa a falhar).
        refreshTokenRepository.revogar(tokenPersistido.id());
        return emitirPar(usuario);
    }

    private TokenPair emitirPar(Usuario usuario) {
        String accessToken = jwtService.emitirAccessToken(usuario.id(), usuario.papel(), usuario.tenantId());

        String refreshBruto = jwtService.gerarRefreshTokenBruto();
        String refreshHash = jwtService.hashRefreshToken(refreshBruto);
        Instant expiraEm = Instant.now().plus(jwtService.refreshTtl());
        refreshTokenRepository.salvar(usuario.id(), refreshHash, expiraEm);

        return new TokenPair(accessToken, refreshBruto);
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }
}

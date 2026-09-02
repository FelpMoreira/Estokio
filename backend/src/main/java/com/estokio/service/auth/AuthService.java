package com.estokio.service.auth;

import com.estokio.domain.user.RefreshToken;
import com.estokio.domain.user.Usuario;
import com.estokio.exception.ApiException;
import com.estokio.repository.auth.RefreshTokenRepository;
import com.estokio.repository.auth.UsuarioRepository;
import com.estokio.security.JwtService;
import com.estokio.security.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

/**
 * Login (email + senha via BCrypt) e refresh (rotaciona o refresh token persistido)
 * -- as duas unicas regras de negocio da Fase 0, para provar JWT + BCrypt + contrato
 * de erro funcionando ponta a ponta.
 */
public final class AuthService {

    /**
     * Hash BCrypt fixo de uma senha que nao corresponde a nenhum usuario real. Usado
     * como alvo da comparacao quando o email nao existe, para que o custo de CPU do
     * BCrypt seja pago nos dois ramos -- sem isso, "email nao encontrado" retorna
     * bem mais rapido que "email encontrado, senha errada", e essa diferenca de
     * tempo reabre a enumeracao de usuario que a mensagem de erro generica tenta evitar.
     */
    private static final String HASH_FANTASMA = PasswordEncoder.hash("hash-fantasma-nao-corresponde-a-nenhuma-senha-real");

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
        Optional<Usuario> usuario = usuarioRepository.buscarPorEmail(email).filter(Usuario::ativo);
        String hashParaComparar = usuario.map(Usuario::senhaHash).orElse(HASH_FANTASMA);
        boolean senhaConfere = PasswordEncoder.confere(senha, hashParaComparar);

        if (usuario.isEmpty() || !senhaConfere) {
            throw ApiException.naoAutorizado("Email ou senha invalidos.");
        }

        return emitirPar(usuario.get());
    }

    public TokenPair refresh(String refreshTokenBruto) {
        String hash = jwtService.hashRefreshToken(refreshTokenBruto);

        // Consumo atomico (compare-and-swap): evita que duas requisicoes concorrentes
        // com o mesmo refresh token bruto rotacionem o mesmo token duas vezes.
        RefreshToken tokenConsumido = refreshTokenRepository.consumirSeValido(hash, Instant.now())
                .orElseThrow(() -> ApiException.naoAutorizado("Refresh token invalido, expirado ou revogado."));

        Usuario usuario = usuarioRepository.buscarPorId(tokenConsumido.usuarioId())
                .filter(Usuario::ativo)
                .orElseThrow(() -> ApiException.naoAutorizado("Usuario nao encontrado ou inativo."));

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

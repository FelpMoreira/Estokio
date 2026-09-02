package com.estokio.security;

import com.estokio.domain.user.Papel;
import com.estokio.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Emissao e validacao de access tokens (JWT assinado HS256) e geracao/hash de
 * refresh tokens opacos (nunca JWT: o valor bruto so existe no cliente, o backend
 * so guarda o hash em {@code refresh_token.token_hash} -- Decisao de kickoff #11).
 */
public final class JwtService {

    private static final String CLAIM_PAPEL = "papel";
    private static final String CLAIM_TENANT_ID = "tenant_id";

    private final SecretKey chave;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom aleatorio = new SecureRandom();

    public JwtService(String segredo, Duration accessTtl, Duration refreshTtl) {
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public String emitirAccessToken(UUID usuarioId, Papel papel, UUID tenantId) {
        Instant agora = Instant.now();
        var builder = Jwts.builder()
                .subject(usuarioId.toString())
                .claim(CLAIM_PAPEL, papel.name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(accessTtl)));
        if (tenantId != null) {
            builder.claim(CLAIM_TENANT_ID, tenantId.toString());
        }
        return builder.signWith(chave).compact();
    }

    public AccessTokenClaims validarAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID usuarioId = UUID.fromString(claims.getSubject());
            Papel papel = Papel.valueOf(claims.get(CLAIM_PAPEL, String.class));
            String tenantIdBruto = claims.get(CLAIM_TENANT_ID, String.class);
            UUID tenantId = tenantIdBruto == null ? null : UUID.fromString(tenantIdBruto);
            return new AccessTokenClaims(usuarioId, papel, tenantId);
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.naoAutorizado("Access token invalido ou expirado.");
        }
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    /** Valor opaco entregue ao cliente. Alta entropia (256 bits), nao e um JWT. */
    public String gerarRefreshTokenBruto() {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hash irreversivel do refresh token, o unico valor persistido em {@code refresh_token}. */
    public String hashRefreshToken(String tokenBruto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(tokenBruto.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }

    public record AccessTokenClaims(UUID usuarioId, Papel papel, UUID tenantId) {
    }
}

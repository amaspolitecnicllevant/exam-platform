package com.examplatform.config;

import com.examplatform.domain.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    /** Valors que han estat públics (codi i exemples): mai no es poden fer servir com a secret. */
    static final java.util.Set<String> SECRETS_PUBLICS = java.util.Set.of(
            "canviam-aquest-secret-en-produccio-min32chars!!");

    /** Versió de token de l'usuari quan es va emetre; si ara és diferent, el token s'ha revocat. */
    static final String CLAU_VERSIO = "sv";
    /** Moment (ms) en què va començar la sessió (login); es conserva en renovar el token. */
    static final String CLAU_INICI = "oiat";

    private final SecretKey key;
    private final long expirationMs;
    private final long maxSessionMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs,
            @Value("${jwt.max-session-ms:43200000}") long maxSessionMs) {
        if (secret == null || secret.isBlank() || SECRETS_PUBLICS.contains(secret)) {
            throw new IllegalStateException("Cal definir JWT_SECRET amb un valor propi i secret "
                    + "(p. ex. `openssl rand -base64 48`): amb un valor conegut, qualsevol podria "
                    + "fabricar-se un token d'administrador");
        }
        // El secret ha de ser Base64 de >= 32 bytes. Si no, usem HMAC directe per compatibilitat dev.
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            keyBytes = secret.getBytes();
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET és massa curt: calen almenys 32 bytes");
        }
        if (maxSessionMs <= 0) {
            throw new IllegalStateException("jwt.max-session-ms ha de ser positiu");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
        this.maxSessionMs = maxSessionMs;
    }

    /** Token d'una sessió nova (login). */
    public String generateToken(UserDetails user) {
        return construeix(user, System.currentTimeMillis());
    }

    /**
     * Renova el token d'una sessió en curs conservant-ne l'inici: així una sessió no es pot allargar per sempre
     * renovant-la. Si ja ha passat la vida màxima, falla.
     */
    public String renova(String tokenActual, UserDetails user) {
        long inici = iniciSessio(parseClaims(tokenActual));
        if (System.currentTimeMillis() - inici > maxSessionMs) {
            throw new JwtException("La sessió ha arribat a la seva vida màxima");
        }
        return construeix(user, inici);
    }

    private String construeix(UserDetails user, long iniciSessio) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("roles", user.getAuthorities().stream()
                        .map(a -> a.getAuthority()).toList())
                .claim(CLAU_VERSIO, versio(user))
                .claim(CLAU_INICI, iniciSessio)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Un token és vàlid si està signat per nosaltres, no ha caducat, és de l'usuari, l'usuari està habilitat,
     * no s'ha revocat (versió de token) i la sessió no ha superat la vida màxima.
     */
    public boolean isValid(String token, UserDetails user) {
        try {
            Claims c = parseClaims(token);
            if (!user.getUsername().equals(c.getSubject())) return false;
            if (c.getExpiration().before(new Date())) return false;
            if (!user.isEnabled()) return false;
            Number sv = c.get(CLAU_VERSIO, Number.class);
            if ((sv == null ? 0 : sv.intValue()) != versio(user)) return false;
            return System.currentTimeMillis() - iniciSessio(c) <= maxSessionMs;
        } catch (JwtException e) {
            return false;
        }
    }

    private static int versio(UserDetails user) {
        return user instanceof User u ? u.getTokenVersion() : 0;
    }

    private static long iniciSessio(Claims c) {
        Number inici = c.get(CLAU_INICI, Number.class);
        // Tokens emesos abans d'aquesta versió: la sessió comença quan es van emetre
        return inici != null ? inici.longValue() : c.getIssuedAt().getTime();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}

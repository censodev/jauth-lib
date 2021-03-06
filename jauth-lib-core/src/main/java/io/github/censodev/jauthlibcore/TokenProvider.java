package io.github.censodev.jauthlibcore;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.security.Keys;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

@SuperBuilder(toBuilder = true)
@Getter
public class TokenProvider {
    @Builder.Default
    private String header = "Authorization";

    @Builder.Default
    private String prefix = "Bearer ";

    @Builder.Default
    private Integer expireInMillisecond = 3_600_000;

    @Builder.Default
    private Integer refreshTokenExpireInMillisecond = 86_400_000;

    @NonNull
    private String secret;

    @Builder.Default
    private String credentialClaimKey = "credential";

    @Builder.Default
    private SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

    @Builder.Default
    private ObjectMapper mapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public <T extends CanAuth> String generateAccessToken(T credential) {
        return generateToken(credential, expireInMillisecond);
    }

    public <T extends CanAuth> String generateRefreshToken(T credential) {
        return generateToken(credential, refreshTokenExpireInMillisecond);
    }

    public Object getPrinciple(String token) {
        return getClaims(token).getSubject();
    }

    public <T extends CanAuth> T getCredential(String token, Class<T> tClass) {
        Map<?, ?> credInMap = getClaims(token).get(credentialClaimKey, Map.class);
        return mapper.convertValue(credInMap, tClass);
    }

    public void validateToken(String token) throws JwtException {
        Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token);
    }

    private <T extends CanAuth> String generateToken(T canAuth, Integer expireInMillisecond) {
        Date now = new Date();
        Date expiredDate = new Date(now.getTime() + expireInMillisecond);
        return Jwts.builder()
                .serializeToJsonWith(new JacksonSerializer<>(mapper))
                .setSubject(canAuth.principle().toString())
                .claim(credentialClaimKey, canAuth)
                .setIssuedAt(now)
                .setExpiration(expiredDate)
                .signWith(getKey(), signatureAlgorithm)
                .compact();
    }

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .deserializeJsonWith(new JacksonDeserializer<>(mapper))
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

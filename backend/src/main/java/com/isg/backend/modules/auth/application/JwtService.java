package com.isg.backend.modules.auth.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    // Token içinden email (username) bilgisini çeker
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Token içinden spesifik bir claim (veri) çeker
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Kullanıcı giriş yaptığında token üretmek için kullanılacak ana metot
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    // Sadece userDetails vererek token üretmek için yardımcı metot
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    // JJWT 0.12.x sürümüne uygun Token Üretme (Builder)
    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        return Jwts.builder()
                .claims(extraClaims) // Güncel kullanım
                .subject(userDetails.getUsername()) // Güncel kullanım
                .issuedAt(new Date(System.currentTimeMillis())) // Güncel kullanım
                .expiration(new Date(System.currentTimeMillis() + expiration)) // Güncel kullanım
                .signWith(getSignInKey()) // Algoritmayı SecretKey üzerinden otomatik algılar
                .compact();
    }

    // Gelen token'ın geçerli olup olmadığını kontrol eder
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // JJWT 0.12.x sürümüne uygun Token Okuma (Parser)
    private Claims extractAllClaims(String token) {
        return Jwts.parser() // parserBuilder() yerine güncel parser()
                .verifyWith(getSignInKey()) // setSigningKey() yerine güncel verifyWith()
                .build()
                .parseSignedClaims(token) // parseClaimsJws() yerine güncel parseSignedClaims()
                .getPayload(); // getBody() yerine güncel getPayload()
    }

    // application.yaml'dan gelen hex formatındaki anahtarı işlenebilir SecretKey formatına çevirir
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
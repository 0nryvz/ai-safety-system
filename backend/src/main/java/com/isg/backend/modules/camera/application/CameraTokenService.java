package com.isg.backend.modules.camera.application;

import com.isg.backend.modules.auth.infrastructure.CameraSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class CameraTokenService {

    private final CameraSecurityProperties properties;

    public CameraTokenService(CameraSecurityProperties properties) {
        this.properties = properties;
    }

    // Mobil uygulama oturum açmak istediğinde bu metot çağrılacak
    public String generateCameraToken(String cameraId) {
        return Jwts.builder()
                .subject(cameraId) // setSubject() yerine güncel kullanım
                .claim("purpose", "CAMERA_SESSION")
                .issuedAt(new Date(System.currentTimeMillis())) // setIssuedAt() yerine güncel kullanım
                .expiration(new Date(System.currentTimeMillis() + properties.getExpiration())) // setExpiration() yerine güncel kullanım
                .signWith(getSignInKey()) // Algoritmayı SecretKey üzerinden otomatik algılar
                .compact();
    }

    // Gelen token'ın içinden cameraId'yi güvenli bir şekilde çıkarıyoruz
    public String extractCameraId(String token) {
        Claims claims = extractAllClaims(token);

        // Token amacının doğrulanması: Web JWT'si ile karışmasını engeller
        if (!"CAMERA_SESSION".equals(claims.get("purpose"))) {
            throw new IllegalArgumentException("Geçersiz token amacı. Sadece kamera oturum token'ları kabul edilir.");
        }

        return claims.getSubject();
    }

    // Token geçerlilik kontrolü
    public boolean isTokenValid(String token, String expectedCameraId) {
        try {
            // Süresi dolmuş (expired) veya imzası bozuk bir token ise,
            // extractCameraId içindeki parse işlemi zaten exception fırlatır ve catch bloğuna düşer.
            final String extractedId = extractCameraId(token);
            return extractedId.equals(expectedCameraId);
        } catch (Exception e) {
            // Token süresi dolmuş, imza hatalı veya amacı "CAMERA_SESSION" değilse geçersiz say
            return false;
        }
    }

    // DRY (Don't Repeat Yourself) prensibi: Parse işlemini tek bir merkeze topladık
    private Claims extractAllClaims(String token) {
        return Jwts.parser() // parserBuilder() yerine güncel parser()
                .verifyWith(getSignInKey()) // setSigningKey() yerine güncel verifyWith()
                .build()
                .parseSignedClaims(token) // parseClaimsJws() yerine güncel parseSignedClaims()
                .getPayload(); // getBody() yerine güncel getPayload()
    }

    // YAML'daki hex/base64 formatlı camera.secret-key'i SecretKey objesine çeviriyoruz
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(properties.getSecretKey());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
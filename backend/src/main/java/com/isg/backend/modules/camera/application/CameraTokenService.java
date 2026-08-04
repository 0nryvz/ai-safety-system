package com.isg.backend.modules.camera.application;

// Kendi klasör yapına uygun olarak "config" kısmı çıkarıldı
import com.isg.backend.modules.auth.infrastructure.CameraSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
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
                .setSubject(cameraId)
                .claim("purpose", "CAMERA_SESSION")
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + properties.getExpiration()))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
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
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // YAML'daki hex/base64 formatlı camera.secret-key'i Key objesine çeviriyoruz
    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(properties.getSecretKey());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
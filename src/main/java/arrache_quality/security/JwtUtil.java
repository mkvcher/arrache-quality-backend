package arrache_quality.security;
 
import java.security.Key;
import java.util.Date;
 
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
 
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
 
@Component
public class JwtUtil {
 
    private final Key key;
    private final long EXPIRATION = 1000L * 60 * 60 * 24; // 24 hours
 
    public JwtUtil(@Value("${app.jwt.secret}") String secret) {
        // Stable key derived from a configured secret — survives server restarts
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }
 
    public String generateToken(String username, String role) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
 
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }
 
    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }
 
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
 
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
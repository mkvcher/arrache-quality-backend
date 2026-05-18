package arrache_quality.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String password;
    private String role; // REPARATEUR, QM_AGENT, LABORATORY, ADMIN
    private String matricule;
    private String fullName;
    private boolean active = true;

    /** Only meaningful when role = REPARATEUR. Each réparateur has exactly one valise. */
    private String valiseId;

    private LocalDateTime createdAt = LocalDateTime.now();
}
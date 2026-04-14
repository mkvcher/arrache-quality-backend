package arrache_quality.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    private String targetRole;
    private String targetUserId;
    private String type;
    private String message;
    private boolean read = false;
    private LocalDateTime createdAt = LocalDateTime.now();
}
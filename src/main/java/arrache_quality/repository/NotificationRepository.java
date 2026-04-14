package arrache_quality.repository;

import arrache_quality.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByTargetRoleAndReadFalse(String targetRole);
    List<Notification> findByTargetUserIdAndReadFalse(String targetUserId);
}
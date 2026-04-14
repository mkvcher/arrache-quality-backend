package arrache_quality.controller;

import arrache_quality.model.Notification;
import arrache_quality.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping("/role/{role}")
    public List<Notification> getByRole(@PathVariable String role) {
        return notificationRepository.findByTargetRoleAndReadFalse(role);
    }

    @GetMapping("/user/{userId}")
    public List<Notification> getByUser(@PathVariable String userId) {
        return notificationRepository.findByTargetUserIdAndReadFalse(userId);
    }

    @PutMapping("/{id}/read")
    public Notification markAsRead(@PathVariable String id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @PostMapping
    public Notification create(@RequestBody Notification notification) {
        return notificationRepository.save(notification);
    }
}
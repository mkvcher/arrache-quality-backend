package arrache_quality.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import arrache_quality.model.Notification;
import arrache_quality.model.User;
import arrache_quality.repository.NotificationRepository;
import arrache_quality.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class NotificationController {

    private static final int RECENT_LIMIT = 30;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Combined notifications for the current user — both role-targeted and user-targeted,
     * read AND unread, limited to the {@value #RECENT_LIMIT} most recent.
     * The frontend uses the `read` flag to dim seen ones and count unread for the badge.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getForCurrentUser() {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();

        List<Notification> combined = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (Notification n : notificationRepository.findAll()) {
            boolean matchesRole = user.getRole() != null && user.getRole().equals(n.getTargetRole());
            boolean matchesUser = user.getId() != null && user.getId().equals(n.getTargetUserId());
            if ((matchesRole || matchesUser) && seenIds.add(n.getId())) {
                combined.add(n);
            }
        }

        // Newest first
        combined.sort(Comparator.comparing(
                Notification::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        // Cap to recent
        if (combined.size() > RECENT_LIMIT) {
            combined = combined.subList(0, RECENT_LIMIT);
        }

        return ResponseEntity.ok(combined);
    }

    /** Mark every unread notification for the current user as read. */
    @PutMapping("/mark-all-read")
    public ResponseEntity<?> markAllRead() {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();

        List<Notification> all = new ArrayList<>();
        if (user.getRole() != null) {
            all.addAll(notificationRepository.findByTargetRoleAndReadFalse(user.getRole()));
        }
        if (user.getId() != null) {
            all.addAll(notificationRepository.findByTargetUserIdAndReadFalse(user.getId()));
        }

        all.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(all);

        return ResponseEntity.ok().build();
    }

    // ───────── existing endpoints kept intact ─────────

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

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }
}
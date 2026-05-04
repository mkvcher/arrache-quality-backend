package arrache_quality.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import arrache_quality.model.Arrache;
import arrache_quality.model.TrackingSheet;
import arrache_quality.model.User;
import arrache_quality.repository.ArracheRepository;
import arrache_quality.repository.NotificationRepository;
import arrache_quality.repository.TrackingSheetRepository;
import arrache_quality.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AdminController {

    private final UserRepository userRepository;
    private final ArracheRepository arracheRepository;
    private final TrackingSheetRepository trackingSheetRepository;
    private final NotificationRepository notificationRepository;

    // Get all users
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // Get all arraches sorted by risk score
    @GetMapping("/arraches/ratings")
    public List<Arrache> getArrachesByRisk() {
        List<Arrache> arraches = arracheRepository.findAll();
        arraches.sort((a, b) -> Double.compare(b.getRiskScore(), a.getRiskScore()));
        return arraches;
    }

    // Get all tracking sheets (activity log)
    @GetMapping("/logs")
    public List<TrackingSheet> getAllSheets() {
        return trackingSheetRepository.findAll();
    }

    // Get dashboard stats
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        List<Arrache> arraches = arracheRepository.findAll();
        long operational = arraches.stream()
            .filter(a -> "OPERATIONAL".equals(a.getStatus())).count();
        long defective = arraches.stream()
            .filter(a -> "DEFECTIVE".equals(a.getStatus())).count();
        long nonConforme = arraches.stream()
            .filter(a -> "NON_CONFORME".equals(a.getStatus())).count();
        long highRisk = arraches.stream()
            .filter(a -> a.getRiskScore() > 60).count();

        return Map.of(
            "totalUsers", userRepository.count(),
            "totalArraches", arraches.size(),
            "operational", operational,
            "defective", defective,
            "nonConforme", nonConforme,
            "highRisk", highRisk,
            "totalSheets", trackingSheetRepository.count()
        );
    }

    // Deactivate a user
    @PutMapping("/users/{id}/deactivate")
    public User deactivateUser(@PathVariable String id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        return userRepository.save(user);
    }
}
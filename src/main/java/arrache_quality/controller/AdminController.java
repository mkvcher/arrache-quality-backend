package arrache_quality.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import arrache_quality.model.Arrache;
import arrache_quality.model.TrackingSheet;
import arrache_quality.model.User;
import arrache_quality.repository.ArracheRepository;
import arrache_quality.repository.NotificationRepository;
import arrache_quality.repository.TrackingSheetRepository;
import arrache_quality.repository.UserRepository;
import arrache_quality.service.ArracheStatsService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AdminController {

    private final UserRepository userRepository;
    private final ArracheRepository arracheRepository;
    private final TrackingSheetRepository trackingSheetRepository;
    @SuppressWarnings("unused")
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final ArracheStatsService arracheStatsService;

    // ───────── READ ─────────

    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/arraches/ratings")
    public List<Arrache> getArrachesByRisk() {
        List<Arrache> arraches = arracheRepository.findAll();
        arraches.sort((a, b) -> Double.compare(b.getRiskScore(), a.getRiskScore()));
        return arraches;
    }

    @GetMapping("/logs")
    public List<TrackingSheet> getAllSheets() {
        return trackingSheetRepository.findAll();
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        List<Arrache> arraches = arracheRepository.findAll();
        long operational = arraches.stream().filter(a -> "OPERATIONAL".equals(a.getStatus())).count();
        long defective   = arraches.stream().filter(a -> "DEFECTIVE".equals(a.getStatus())).count();
        long nonConforme = arraches.stream().filter(a -> "NON_CONFORME".equals(a.getStatus())).count();
        long highRisk    = arraches.stream().filter(a -> a.getRiskScore() > 60).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalArraches", arraches.size());
        stats.put("operational", operational);
        stats.put("defective", defective);
        stats.put("nonConforme", nonConforme);
        stats.put("highRisk", highRisk);
        stats.put("totalSheets", trackingSheetRepository.count());
        return stats;
    }

    /** One-shot backfill: scans full history and recomputes risk stats for every arrache. */
    @PostMapping("/stats/recompute")
    public Map<String, Object> recomputeStats() {
        int count = arracheStatsService.recomputeAll();
        return Map.of("recomputed", count);
    }

    // ───────── CREATE ─────────

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody User payload) {
        if (payload.getUsername() == null || userRepository.findByUsername(payload.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        if (payload.getPassword() == null || payload.getPassword().isEmpty()) {
            return ResponseEntity.badRequest().body("Password is required");
        }

        payload.setPassword(passwordEncoder.encode(payload.getPassword()));
        payload.setActive(true);

        if ("REPARATEUR".equals(payload.getRole())
                && payload.getValiseId() != null
                && !payload.getValiseId().isEmpty()) {
            clearValiseFromOtherUsers(payload.getValiseId(), null);
        } else {
            payload.setValiseId(null);
        }

        return ResponseEntity.ok(userRepository.save(payload));
    }

    // ───────── UPDATE ─────────

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable String id, @RequestBody Map<String, Object> updates) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (updates.containsKey("fullName"))  user.setFullName((String) updates.get("fullName"));
        if (updates.containsKey("matricule")) user.setMatricule((String) updates.get("matricule"));
        if (updates.containsKey("role"))      user.setRole((String) updates.get("role"));

        if (updates.containsKey("valiseId")) {
            String newValiseId = (String) updates.get("valiseId");
            if (newValiseId == null || newValiseId.isEmpty()) {
                user.setValiseId(null);
            } else if ("REPARATEUR".equals(user.getRole())) {
                clearValiseFromOtherUsers(newValiseId, id);
                user.setValiseId(newValiseId);
            }
        }

        if (!"REPARATEUR".equals(user.getRole())) {
            user.setValiseId(null);
        }

        return ResponseEntity.ok(userRepository.save(user));
    }

    @PutMapping("/users/{id}/deactivate")
    public User deactivateUser(@PathVariable String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        return userRepository.save(user);
    }

    @PutMapping("/users/{id}/activate")
    public User activateUser(@PathVariable String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        return userRepository.save(user);
    }

    // ───────── helpers ─────────

    private void clearValiseFromOtherUsers(String valiseId, String exceptUserId) {
        if (valiseId == null) return;
        userRepository.findAll().stream()
                .filter(u -> valiseId.equals(u.getValiseId())
                          && (exceptUserId == null || !u.getId().equals(exceptUserId)))
                .forEach(u -> {
                    u.setValiseId(null);
                    userRepository.save(u);
                });
    }
}
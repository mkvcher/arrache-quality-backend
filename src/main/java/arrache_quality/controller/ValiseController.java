package arrache_quality.controller;
 
import java.util.List;
 
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
 
import arrache_quality.model.Arrache;
import arrache_quality.model.User;
import arrache_quality.model.Valise;
import arrache_quality.repository.ArracheRepository;
import arrache_quality.repository.UserRepository;
import arrache_quality.repository.ValiseRepository;
import lombok.RequiredArgsConstructor;
 
@RestController
@RequestMapping("/api/valises")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ValiseController {
 
    private final ValiseRepository valiseRepository;
    private final ArracheRepository arracheRepository;
    private final UserRepository userRepository;
 
    @GetMapping
    public List<Valise> getAll() {
        return valiseRepository.findAll();
    }
 
    @GetMapping("/{id}")
    public ResponseEntity<Valise> getById(@PathVariable String id) {
        return valiseRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
 
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Valise valise) {
        if (valise.getValiseNumber() == null || valise.getValiseNumber().isBlank()) {
            return ResponseEntity.badRequest().body("valiseNumber is required");
        }
        // Mongo's unique index will throw if duplicated, but let's check first for a cleaner message
        boolean exists = valiseRepository.findAll().stream()
                .anyMatch(v -> valise.getValiseNumber().equals(v.getValiseNumber()));
        if (exists) {
            return ResponseEntity.badRequest().body("Une valise avec ce numéro existe déjà");
        }
        return ResponseEntity.ok(valiseRepository.save(valise));
    }
 
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Valise payload) {
        Valise existing = valiseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Valise not found"));
 
        // If renaming, ensure the new number doesn't collide
        if (payload.getValiseNumber() != null
                && !payload.getValiseNumber().equals(existing.getValiseNumber())) {
            boolean clash = valiseRepository.findAll().stream()
                    .anyMatch(v -> !v.getId().equals(id)
                                && payload.getValiseNumber().equals(v.getValiseNumber()));
            if (clash) {
                return ResponseEntity.badRequest().body("Une valise avec ce numéro existe déjà");
            }
            existing.setValiseNumber(payload.getValiseNumber());
        }
 
        if (payload.getPf() != null)       existing.setPf(payload.getPf());
        if (payload.getSegment() != null)  existing.setSegment(payload.getSegment());
        if (payload.getLocation() != null) existing.setLocation(payload.getLocation());
        if (payload.getStatus() != null)   existing.setStatus(payload.getStatus());
 
        return ResponseEntity.ok(valiseRepository.save(existing));
    }
 
    /**
     * Delete a valise. Cascades:
     *  - all its arraches are deleted
     *  - any user assigned to it has their valiseId cleared
     * Tracking sheets remain (immutable audit trail; they store valiseNumber for history).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        if (!valiseRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
 
        // 1. Delete all arraches in this valise
        List<Arrache> arraches = arracheRepository.findByValiseId(id);
        if (!arraches.isEmpty()) {
            arracheRepository.deleteAll(arraches);
        }
 
        // 2. Clear assignment from any user holding this valise
        for (User u : userRepository.findAll()) {
            if (id.equals(u.getValiseId())) {
                u.setValiseId(null);
                userRepository.save(u);
            }
        }
 
        // 3. Delete the valise itself
        valiseRepository.deleteById(id);
 
        return ResponseEntity.ok().build();
    }
}
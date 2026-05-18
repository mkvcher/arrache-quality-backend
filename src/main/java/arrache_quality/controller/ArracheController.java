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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
 
import arrache_quality.model.Arrache;
import arrache_quality.repository.ArracheRepository;
import lombok.RequiredArgsConstructor;
 
@RestController
@RequestMapping("/api/arraches")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ArracheController {
 
    private final ArracheRepository arracheRepository;
 
    @GetMapping
    public List<Arrache> getAll() {
        return arracheRepository.findAll();
    }
 
    @GetMapping("/{id}")
    public ResponseEntity<Arrache> getById(@PathVariable String id) {
        return arracheRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
 
    @GetMapping("/valise/{valiseId}")
    public List<Arrache> getByValise(@PathVariable String valiseId) {
        return arracheRepository.findByValiseId(valiseId);
    }
 
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Arrache arrache) {
        if (arrache.getArracheNumber() == null || arrache.getArracheNumber().isBlank()) {
            return ResponseEntity.badRequest().body("arracheNumber is required");
        }
        if (arrache.getValiseId() == null || arrache.getValiseId().isBlank()) {
            return ResponseEntity.badRequest().body("valiseId is required");
        }
        boolean clash = arracheRepository.findAll().stream()
                .anyMatch(a -> arrache.getArracheNumber().equals(a.getArracheNumber()));
        if (clash) {
            return ResponseEntity.badRequest().body("Un arrache avec ce numéro existe déjà");
        }
        return ResponseEntity.ok(arracheRepository.save(arrache));
    }
 
    /**
     * Full update of editable identity fields. Stats fields (riskScore, totalNokCount, etc.)
     * are intentionally NOT mutated here — those are owned by the inspection workflow.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Arrache payload) {
        Arrache existing = arracheRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Arrache not found"));
 
        if (payload.getArracheNumber() != null
                && !payload.getArracheNumber().equals(existing.getArracheNumber())) {
            boolean clash = arracheRepository.findAll().stream()
                    .anyMatch(a -> !a.getId().equals(id)
                                && payload.getArracheNumber().equals(a.getArracheNumber()));
            if (clash) {
                return ResponseEntity.badRequest().body("Un arrache avec ce numéro existe déjà");
            }
            existing.setArracheNumber(payload.getArracheNumber());
        }
 
        if (payload.getToolDescription() != null) existing.setToolDescription(payload.getToolDescription());
        if (payload.getPositionInValise() > 0)    existing.setPositionInValise(payload.getPositionInValise());
        // valiseId is not changeable through this endpoint — would orphan the arrache otherwise
 
        return ResponseEntity.ok(arracheRepository.save(existing));
    }
 
    /** Used by the laboratory verdict flow — kept intact. */
    @PutMapping("/{id}/status")
    public Arrache updateStatus(@PathVariable String id, @RequestParam String status) {
        Arrache arrache = arracheRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Arrache not found"));
        arrache.setStatus(status);
        return arracheRepository.save(arrache);
    }
 
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        if (!arracheRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        arracheRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
package arrache_quality.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
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

    @GetMapping("/valise/{valiseId}")
    public List<Arrache> getByValise(@PathVariable String valiseId) {
        return arracheRepository.findByValiseId(valiseId);
    }

    @PostMapping
    public Arrache create(@RequestBody Arrache arrache) {
        return arracheRepository.save(arrache);
    }

    @PutMapping("/{id}/status")
    public Arrache updateStatus(@PathVariable String id, @RequestParam String status) {
        Arrache arrache = arracheRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Arrache not found"));
        arrache.setStatus(status);
        return arracheRepository.save(arrache);
    }
}
package arrache_quality.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import arrache_quality.model.Valise;
import arrache_quality.repository.ValiseRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/valises")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ValiseController {

    private final ValiseRepository valiseRepository;

    @GetMapping
    public List<Valise> getAll() {
        return valiseRepository.findAll();
    }

    @PostMapping
    public Valise create(@RequestBody Valise valise) {
        return valiseRepository.save(valise);
    }
}
package arrache_quality.controller;
 
import java.util.List;
 
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
 
import arrache_quality.model.TrackingSheet;
import arrache_quality.repository.TrackingSheetRepository;
import lombok.RequiredArgsConstructor;
 
@RestController
@RequestMapping("/api/sheets")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class TrackingSheetController {
 
    private final TrackingSheetRepository trackingSheetRepository;
 
    // Create a new sheet for a valise + quarter
    @PostMapping
    public TrackingSheet create(@RequestBody TrackingSheet sheet) {
        return trackingSheetRepository.save(sheet);
    }
 
    // Get sheet by valiseId + quarter + year — returns null (200) when not found
    // so the frontend can decide to create a new one
    @GetMapping
    public ResponseEntity<TrackingSheet> getSheet(
            @RequestParam String valiseId,
            @RequestParam int quarter,
            @RequestParam int year) {
        return trackingSheetRepository
                .findByValiseIdAndQuarterAndYear(valiseId, quarter, year)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(null));
    }
 
    // Get all sheets for a valise
    @GetMapping("/valise/{valiseId}")
    public List<TrackingSheet> getByValise(@PathVariable String valiseId) {
        return trackingSheetRepository.findByValiseId(valiseId);
    }
 
    // Submit a weekly check (Sem.)
    @PostMapping("/{id}/weekly")
    public TrackingSheet addWeeklyCheck(
            @PathVariable String id,
            @RequestBody TrackingSheet.WeeklyCheck weeklyCheck) {
        TrackingSheet sheet = trackingSheetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sheet not found"));
        sheet.getWeeklyChecks().add(weeklyCheck);
        return trackingSheetRepository.save(sheet);
    }
 
    // Submit a monthly check (QM OP.)
    @PostMapping("/{id}/monthly")
    public TrackingSheet addMonthlyCheck(
            @PathVariable String id,
            @RequestBody TrackingSheet.MonthlyCheck monthlyCheck) {
        TrackingSheet sheet = trackingSheetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sheet not found"));
        sheet.getMonthlyChecks().add(monthlyCheck);
        return trackingSheetRepository.save(sheet);
    }
 
    // Submit quarterly verdict (QM Labo)
    @PostMapping("/{id}/quarterly")
    public TrackingSheet addQuarterlyVerdict(
            @PathVariable String id,
            @RequestBody TrackingSheet.QuarterlyVerdict verdict) {
        TrackingSheet sheet = trackingSheetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sheet not found"));
        sheet.setQuarterlyVerdict(verdict);
        return trackingSheetRepository.save(sheet);
    }
}
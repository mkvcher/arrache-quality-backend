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
import arrache_quality.service.ArracheStatsService;
import arrache_quality.service.NotificationService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sheets")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class TrackingSheetController {

    private final TrackingSheetRepository trackingSheetRepository;
    private final NotificationService notificationService;
    private final ArracheStatsService arracheStatsService;

    @PostMapping
    public TrackingSheet create(@RequestBody TrackingSheet sheet) {
        return trackingSheetRepository.save(sheet);
    }

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

    @GetMapping("/valise/{valiseId}")
    public List<TrackingSheet> getByValise(@PathVariable String valiseId) {
        return trackingSheetRepository.findByValiseId(valiseId);
    }

    @PostMapping("/{id}/weekly")
    public TrackingSheet addWeeklyCheck(
            @PathVariable String id,
            @RequestBody TrackingSheet.WeeklyCheck weeklyCheck) {
        TrackingSheet sheet = trackingSheetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sheet not found"));
        sheet.getWeeklyChecks().add(weeklyCheck);
        TrackingSheet saved = trackingSheetRepository.save(sheet);

        // 1) Notifications (rupture + pattern detection)
        notificationService.afterWeeklyCheck(saved, weeklyCheck);
        // 2) Recompute risk stats for every arrache in this sheet
        arracheStatsService.recomputeForSheet(saved);

        return saved;
    }

    @PostMapping("/{id}/monthly")
    public TrackingSheet addMonthlyCheck(
            @PathVariable String id,
            @RequestBody TrackingSheet.MonthlyCheck monthlyCheck) {
        TrackingSheet sheet = trackingSheetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sheet not found"));
        sheet.getMonthlyChecks().add(monthlyCheck);
        TrackingSheet saved = trackingSheetRepository.save(sheet);

        notificationService.afterMonthlyCheck(saved, monthlyCheck);
        arracheStatsService.recomputeForSheet(saved);

        return saved;
    }

    @PostMapping("/{id}/quarterly")
    public TrackingSheet addQuarterlyVerdict(
            @PathVariable String id,
            @RequestBody TrackingSheet.QuarterlyVerdict verdict) {
        TrackingSheet sheet = trackingSheetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sheet not found"));
        sheet.setQuarterlyVerdict(verdict);
        TrackingSheet saved = trackingSheetRepository.save(sheet);

        notificationService.afterQuarterlyVerdict(saved, verdict);
        arracheStatsService.recomputeForSheet(saved);

        return saved;
    }
}
package arrache_quality.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import arrache_quality.model.TrackingSheet;
import arrache_quality.model.User;
import arrache_quality.model.Valise;
import arrache_quality.repository.TrackingSheetRepository;
import arrache_quality.repository.ValiseRepository;
import lombok.RequiredArgsConstructor;

/**
 * Runs every day at 08:00 (server time) to detect valises that haven't had
 * a weekly check submitted in too long.
 *
 *   ≥ 8 days  → notify the assigned réparateur
 *   ≥ 14 days → also notify ADMIN role (critical)
 *
 * Anti-spam is handled by NotificationService — the same alert won't fire
 * again until the recipient marks it read.
 */
@Component
@RequiredArgsConstructor
public class OverdueCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueCheckScheduler.class);

    private static final int OVERDUE_DAYS_REPARATEUR = 8;
    private static final int OVERDUE_DAYS_CRITICAL   = 14;

    private final ValiseRepository valiseRepository;
    private final TrackingSheetRepository trackingSheetRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 8 * * *") // every day at 08:00
    public void detectOverdue() {
        log.info("[scheduler] Running overdue weekly check detection");

        LocalDate today = LocalDate.now();
        int quarter = (today.getMonthValue() - 1) / 3 + 1;
        int year = today.getYear();
        LocalDateTime now = LocalDateTime.now();

        int firedReparateur = 0;
        int firedAdmin = 0;

        for (Valise v : valiseRepository.findAll()) {
            // Skip valises with no réparateur assigned
            var reparateurs = notificationService.findActiveReparateurForValise(v.getId());
            if (reparateurs.isEmpty()) continue;
            User reparateur = reparateurs.get(0);

            // Get this quarter's tracking sheet (if any)
            TrackingSheet sheet = trackingSheetRepository
                    .findByValiseIdAndQuarterAndYear(v.getId(), quarter, year)
                    .orElse(null);

            if (sheet == null) continue;  // No quarter sheet — not yet started, not "overdue"

            // Find the most recent weekly check date, or fall back to sheet creation
            LocalDateTime baseline = sheet.getWeeklyChecks().stream()
                    .map(TrackingSheet.WeeklyCheck::getDate)
                    .filter(d -> d != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(sheet.getCreatedAt());

            if (baseline == null) continue;

            long daysSince = ChronoUnit.DAYS.between(baseline.toLocalDate(), today);

            if (daysSince >= OVERDUE_DAYS_CRITICAL) {
                notificationService.onAdminCriticalOverdue(v.getValiseNumber(), (int) daysSince);
                firedAdmin++;
            }
            if (daysSince >= OVERDUE_DAYS_REPARATEUR) {
                notificationService.onOverdueWeekly(reparateur.getId(), v.getValiseNumber(), (int) daysSince);
                firedReparateur++;
            }
        }

        log.info("[scheduler] Overdue scan done — réparateur alerts: {}, admin alerts: {}",
                firedReparateur, firedAdmin);
    }
}
package arrache_quality.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import arrache_quality.model.Arrache;
import arrache_quality.model.TrackingSheet;
import arrache_quality.repository.ArracheRepository;
import arrache_quality.repository.TrackingSheetRepository;
import lombok.RequiredArgsConstructor;

/**
 * Recomputes derived statistics for arraches from the full inspection history.
 *
 * Risk score formula (0–100):
 *   · 40% — weighted NOK frequency (weekly ×1, monthly ×2, quarterly ×3)
 *   · 30% — rupture ever observed (binary)
 *   · 20% — consecutive NOK weeks (5 pts per week, capped at 4 = 20)
 *   · 10% — age in days (full at 365 days)
 *
 * The weighting reflects inspection seniority: the laboratory's quarterly verdict
 * is the authoritative one, the QM agent's monthly check is intermediate, and the
 * réparateur's weekly check is the day-to-day pulse.
 *
 * Status auto-update (only when not already NON_CONFORME — lab decision is sticky):
 *   · rupture ever seen → NON_CONFORME
 *   · risk > 60         → DEFECTIVE
 *   · else              → OPERATIONAL
 */
@Service
@RequiredArgsConstructor
public class ArracheStatsService {

    private static final Logger log = LoggerFactory.getLogger(ArracheStatsService.class);

    private static final double WEIGHT_FREQUENCY   = 40.0;
    private static final double WEIGHT_RUPTURE     = 30.0;
    private static final double WEIGHT_CONSECUTIVE = 20.0;
    private static final double WEIGHT_AGE         = 10.0;

    // Inspection-type weights for the NOK frequency component
    private static final int WEEKLY_WEIGHT    = 1;
    private static final int MONTHLY_WEIGHT   = 2;
    private static final int QUARTERLY_WEIGHT = 3;

    private static final double DEFECTIVE_THRESHOLD = 60.0;

    private final ArracheRepository arracheRepository;
    private final TrackingSheetRepository trackingSheetRepository;

    // ───────── Public API ─────────

    public void recomputeForSheet(TrackingSheet sheet) {
        if (sheet == null) return;
        Set<String> arracheIds = collectArracheIds(sheet);
        arracheIds.forEach(this::recomputeForArrache);
    }

    public int recomputeAll() {
        List<Arrache> all = arracheRepository.findAll();
        all.forEach(a -> recomputeForArrache(a.getId()));
        log.info("[stats] Recomputed {} arraches", all.size());
        return all.size();
    }

    public void recomputeForArrache(String arracheId) {
        if (arracheId == null) return;
        Arrache arrache = arracheRepository.findById(arracheId).orElse(null);
        if (arrache == null) return;

        // Raw counts for display + weighted accumulators for the score
        int totalNok = 0;
        boolean ruptureSeen = false;

        int weightedNok = 0;     // Σ (weight × isNok)
        int weightedTotal = 0;   // Σ (weight × 1 per check involving this arrache)

        List<WeeklyEntry> weeklyEntries = new ArrayList<>();

        for (TrackingSheet sheet : trackingSheetRepository.findAll()) {
            // Weekly
            if (sheet.getWeeklyChecks() != null) {
                for (TrackingSheet.WeeklyCheck wc : sheet.getWeeklyChecks()) {
                    if (wc.getResults() == null) continue;
                    for (TrackingSheet.ArracheResult r : wc.getResults()) {
                        if (!arracheId.equals(r.getArracheId())) continue;
                        boolean isNok = "NOK".equals(r.getResult());
                        weeklyEntries.add(new WeeklyEntry(sheet.getYear(), wc.getWeekNumber(), isNok));
                        weightedTotal += WEEKLY_WEIGHT;
                        if (isNok) {
                            totalNok++;
                            weightedNok += WEEKLY_WEIGHT;
                            if (r.getNokReasons() != null && r.getNokReasons().isRupture()) {
                                ruptureSeen = true;
                            }
                        }
                    }
                }
            }
            // Monthly
            if (sheet.getMonthlyChecks() != null) {
                for (TrackingSheet.MonthlyCheck mc : sheet.getMonthlyChecks()) {
                    if (mc.getResults() == null) continue;
                    for (TrackingSheet.ArracheResult r : mc.getResults()) {
                        if (!arracheId.equals(r.getArracheId())) continue;
                        weightedTotal += MONTHLY_WEIGHT;
                        if ("NOK".equals(r.getResult())) {
                            totalNok++;
                            weightedNok += MONTHLY_WEIGHT;
                            if (r.getNokReasons() != null && r.getNokReasons().isRupture()) {
                                ruptureSeen = true;
                            }
                        }
                    }
                }
            }
            // Quarterly verdict
            if (sheet.getQuarterlyVerdict() != null && sheet.getQuarterlyVerdict().getResults() != null) {
                for (TrackingSheet.ArracheResult r : sheet.getQuarterlyVerdict().getResults()) {
                    if (!arracheId.equals(r.getArracheId())) continue;
                    weightedTotal += QUARTERLY_WEIGHT;
                    if ("NOK".equals(r.getResult())) {
                        totalNok++;
                        weightedNok += QUARTERLY_WEIGHT;
                        if (r.getNokReasons() != null && r.getNokReasons().isRupture()) {
                            ruptureSeen = true;
                        }
                    }
                }
            }
        }

        // ── Consecutive NOK (trailing NOKs in chronological weekly history) ──
        weeklyEntries.sort(Comparator
                .comparingInt(WeeklyEntry::year)
                .thenComparingInt(WeeklyEntry::week));
        int consecutiveNok = 0;
        for (int i = weeklyEntries.size() - 1; i >= 0; i--) {
            if (weeklyEntries.get(i).nok()) consecutiveNok++;
            else break;
        }

        // ── Risk score components ──
        double frequencyScore = (weightedTotal == 0)
                ? 0.0
                : ((double) weightedNok / weightedTotal) * WEIGHT_FREQUENCY;

        double ruptureScore = ruptureSeen ? WEIGHT_RUPTURE : 0.0;

        double consecutiveScore = Math.min(WEIGHT_CONSECUTIVE, consecutiveNok * 5.0);

        double ageScore = 0.0;
        if (arrache.getCreatedAt() != null) {
            long days = ChronoUnit.DAYS.between(arrache.getCreatedAt(), LocalDateTime.now());
            ageScore = Math.min(WEIGHT_AGE, days / 36.5);
        }

        double riskScore = frequencyScore + ruptureScore + consecutiveScore + ageScore;
        if (riskScore < 0)   riskScore = 0;
        if (riskScore > 100) riskScore = 100;

        // ── Apply ──
        arrache.setTotalNokCount(totalNok);
        arrache.setRuptureEverSeen(ruptureSeen);
        arrache.setConsecutiveNokWeeks(consecutiveNok);
        arrache.setRiskScore(riskScore);

        if (!"NON_CONFORME".equals(arrache.getStatus())) {
            if (ruptureSeen) {
                arrache.setStatus("NON_CONFORME");
            } else if (riskScore > DEFECTIVE_THRESHOLD) {
                arrache.setStatus("DEFECTIVE");
            } else {
                arrache.setStatus("OPERATIONAL");
            }
        }

        arracheRepository.save(arrache);
    }

    // ───────── helpers ─────────

    private Set<String> collectArracheIds(TrackingSheet sheet) {
        Set<String> ids = new HashSet<>();
        if (sheet.getWeeklyChecks() != null) {
            sheet.getWeeklyChecks().forEach(wc -> {
                if (wc.getResults() != null)
                    wc.getResults().forEach(r -> { if (r.getArracheId() != null) ids.add(r.getArracheId()); });
            });
        }
        if (sheet.getMonthlyChecks() != null) {
            sheet.getMonthlyChecks().forEach(mc -> {
                if (mc.getResults() != null)
                    mc.getResults().forEach(r -> { if (r.getArracheId() != null) ids.add(r.getArracheId()); });
            });
        }
        if (sheet.getQuarterlyVerdict() != null && sheet.getQuarterlyVerdict().getResults() != null) {
            sheet.getQuarterlyVerdict().getResults().forEach(
                    r -> { if (r.getArracheId() != null) ids.add(r.getArracheId()); });
        }
        return ids;
    }

    private record WeeklyEntry(int year, int week, boolean nok) {}
}
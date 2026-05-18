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
 * Recomputes derived statistics for arraches based on the full inspection history.
 *
 * Risk score formula (0–100):
 *   · 40% — weekly NOK frequency (weeklyNokCount / weeklyTotal)
 *   · 30% — rupture ever observed (binary)
 *   · 20% — consecutive NOK weeks (5 pts per week, capped at 4 = 20)
 *   · 10% — age in days (full at 365 days)
 *
 * Status auto-update (only when not already NON_CONFORME, which is owned by the lab):
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

    private static final double DEFECTIVE_THRESHOLD = 60.0;

    private final ArracheRepository arracheRepository;
    private final TrackingSheetRepository trackingSheetRepository;

    // ───────── Public API ─────────

    /** Recompute stats for every arrache mentioned anywhere in the sheet. */
    public void recomputeForSheet(TrackingSheet sheet) {
        if (sheet == null) return;
        Set<String> arracheIds = collectArracheIds(sheet);
        arracheIds.forEach(this::recomputeForArrache);
    }

    /** Backfill helper — recomputes every arrache in the database. */
    public int recomputeAll() {
        List<Arrache> all = arracheRepository.findAll();
        all.forEach(a -> recomputeForArrache(a.getId()));
        log.info("[stats] Recomputed {} arraches", all.size());
        return all.size();
    }

    /** Core: rebuild every derived field for a single arrache from full history. */
    public void recomputeForArrache(String arracheId) {
        if (arracheId == null) return;
        Arrache arrache = arracheRepository.findById(arracheId).orElse(null);
        if (arrache == null) return;

        int totalNok = 0;
        int totalChecks = 0;
        boolean ruptureSeen = false;
        List<WeeklyEntry> weeklyEntries = new ArrayList<>();

        for (TrackingSheet sheet : trackingSheetRepository.findAll()) {
            // Weekly
            if (sheet.getWeeklyChecks() != null) {
                for (TrackingSheet.WeeklyCheck wc : sheet.getWeeklyChecks()) {
                    if (wc.getResults() == null) continue;
                    for (TrackingSheet.ArracheResult r : wc.getResults()) {
                        if (!arracheId.equals(r.getArracheId())) continue;
                        totalChecks++;
                        boolean isNok = "NOK".equals(r.getResult());
                        weeklyEntries.add(new WeeklyEntry(sheet.getYear(), wc.getWeekNumber(), isNok));
                        if (isNok) {
                            totalNok++;
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
                        totalChecks++;
                        if ("NOK".equals(r.getResult())) {
                            totalNok++;
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
                    totalChecks++;
                    if ("NOK".equals(r.getResult())) {
                        totalNok++;
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
        long weeklyNok = weeklyEntries.stream().filter(WeeklyEntry::nok).count();
        int weeklyTotal = weeklyEntries.size();

        double frequencyScore = (weeklyTotal == 0)
                ? 0.0
                : ((double) weeklyNok / weeklyTotal) * WEIGHT_FREQUENCY;

        double ruptureScore = ruptureSeen ? WEIGHT_RUPTURE : 0.0;

        double consecutiveScore = Math.min(WEIGHT_CONSECUTIVE, consecutiveNok * 5.0);

        double ageScore = 0.0;
        if (arrache.getCreatedAt() != null) {
            long days = ChronoUnit.DAYS.between(arrache.getCreatedAt(), LocalDateTime.now());
            ageScore = Math.min(WEIGHT_AGE, days / 36.5);
        }

        double riskScore = frequencyScore + ruptureScore + consecutiveScore + ageScore;
        // Safety clamp
        if (riskScore < 0)   riskScore = 0;
        if (riskScore > 100) riskScore = 100;

        // ── Apply ──
        arrache.setTotalNokCount(totalNok);
        arrache.setRuptureEverSeen(ruptureSeen);
        arrache.setConsecutiveNokWeeks(consecutiveNok);
        arrache.setRiskScore(riskScore);

        // Status: lab-issued NON_CONFORME is sticky; otherwise auto-derive
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

        // Silence the unused-warning the IDE might emit
        if (totalChecks < 0) log.debug("unreachable");
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
package arrache_quality.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import arrache_quality.model.Notification;
import arrache_quality.model.TrackingSheet;
import arrache_quality.model.TrackingSheet.ArracheResult;
import arrache_quality.model.TrackingSheet.WeeklyCheck;
import arrache_quality.model.User;
import arrache_quality.repository.NotificationRepository;
import arrache_quality.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Centralized notification dispatch.
 *
 * Anti-spam guard: before creating a notification, checks if an *unread*
 * notification with the same type and message already exists for the target.
 * If so, the new one is skipped. Once the user reads the notification,
 * a fresh one can be sent. This keeps the daily scheduler from spamming.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // Notification types — used by the frontend to colorize/icon
    public static final String TYPE_RUPTURE                  = "RUPTURE";
    public static final String TYPE_QUARTERLY_NOK            = "QUARTERLY_NOK";
    public static final String TYPE_QUALITY_DECLINE          = "QUALITY_DECLINE";
    public static final String TYPE_OVERDUE_WEEKLY           = "OVERDUE_WEEKLY";
    public static final String TYPE_OVERDUE_WEEKLY_CRITICAL  = "OVERDUE_WEEKLY_CRITICAL";

    // ───────── Low-level dispatch ─────────

    public void notifyRole(String role, String type, String message) {
        boolean duplicate = notificationRepository.findByTargetRoleAndReadFalse(role).stream()
                .anyMatch(n -> type.equals(n.getType()) && message.equals(n.getMessage()));
        if (duplicate) return;

        Notification n = new Notification();
        n.setTargetRole(role);
        n.setType(type);
        n.setMessage(message);
        notificationRepository.save(n);
    }

    public void notifyUser(String userId, String type, String message) {
        if (userId == null) return;
        boolean duplicate = notificationRepository.findByTargetUserIdAndReadFalse(userId).stream()
                .anyMatch(n -> type.equals(n.getType()) && message.equals(n.getMessage()));
        if (duplicate) return;

        Notification n = new Notification();
        n.setTargetUserId(userId);
        n.setType(type);
        n.setMessage(message);
        notificationRepository.save(n);
    }

    /** Send to whichever active réparateur is assigned to a valise (if any). */
    public void notifyValiseReparateur(String valiseId, String type, String message) {
        if (valiseId == null) return;
        userRepository.findAll().stream()
                .filter(u -> valiseId.equals(u.getValiseId())
                          && "REPARATEUR".equals(u.getRole())
                          && u.isActive())
                .findFirst()
                .ifPresent(u -> notifyUser(u.getId(), type, message));
    }

    // ───────── Event-specific helpers ─────────

    public void onRuptureDetected(String valiseNumber, int position, String inspector, String checkType) {
        String msg = "Rupture détectée — " + valiseNumber + " arrache pos." + position
                   + " (" + checkType + " par " + (inspector != null ? inspector : "—") + ")";
        notifyRole("LABORATORY", TYPE_RUPTURE, msg);
        notifyRole("ADMIN",      TYPE_RUPTURE, msg);
    }

    public void onQuarterlyVerdictNok(String valiseId, String valiseNumber, int nokCount) {
        String msg = "Verdict trimestriel — " + valiseNumber + " : " + nokCount
                   + " arrache" + (nokCount > 1 ? "s" : "") + " marqué"
                   + (nokCount > 1 ? "s" : "") + " NOK par le laboratoire";
        notifyRole("ADMIN", TYPE_QUARTERLY_NOK, msg);
        notifyValiseReparateur(valiseId, TYPE_QUARTERLY_NOK, msg);
    }

    public void onOverdueWeekly(String userId, String valiseNumber, int days) {
        String msg = "Contrôle hebdomadaire en retard — " + valiseNumber
                   + " : aucun contrôle depuis " + days + " jours";
        notifyUser(userId, TYPE_OVERDUE_WEEKLY, msg);
    }

    public void onOverdueWeeklyCritical(String valiseNumber, int days) {
        String msg = "ALERTE — " + valiseNumber + " : aucun contrôle hebdomadaire depuis "
                   + days + " jours (seuil critique dépassé)";
        notifyRole("ADMIN", TYPE_OVERDUE_WEEKLY_CRITICAL, msg);
    }

    // ───────── Pattern detection: 3+ NOK in last 4 weekly checks ─────────

    /**
     * Inspects the latest weekly checks of a sheet and notifies QM_AGENT if any
     * arrache has been NOK in 3 or more of the last 4 weekly checks.
     */
    public void checkAndNotifyPattern(TrackingSheet sheet) {
        if (sheet == null || sheet.getWeeklyChecks() == null) return;

        // Take the last 4 weekly checks (by week number, descending)
        List<WeeklyCheck> recent = sheet.getWeeklyChecks().stream()
                .sorted((a, b) -> b.getWeekNumber() - a.getWeekNumber())
                .limit(4)
                .toList();

        if (recent.size() < 3) return;

        // Count NOK occurrences per arrache id
        Map<String, Integer> nokCounts = new HashMap<>();
        Map<String, Integer> arrachePositions = new HashMap<>();
        for (WeeklyCheck wc : recent) {
            if (wc.getResults() == null) continue;
            for (ArracheResult r : wc.getResults()) {
                arrachePositions.putIfAbsent(r.getArracheId(), r.getPositionInValise());
                if ("NOK".equals(r.getResult())) {
                    nokCounts.merge(r.getArracheId(), 1, Integer::sum);
                }
            }
        }

        Set<String> alerted = new HashSet<>();
        for (Map.Entry<String, Integer> e : nokCounts.entrySet()) {
            if (e.getValue() >= 3 && !alerted.contains(e.getKey())) {
                Integer pos = arrachePositions.get(e.getKey());
                String msg = "Tendance qualité — " + sheet.getValiseNumber()
                           + " arrache pos." + (pos != null ? pos : "?")
                           + " : " + e.getValue() + " NOK sur les "
                           + recent.size() + " dernières semaines";
                notifyRole("QM_AGENT", TYPE_QUALITY_DECLINE, msg);
                alerted.add(e.getKey());
            }
        }
    }

    // ───────── Inspection-event entry points (used by TrackingSheetController) ─────────

    /**
     * Called after a weekly check is saved.
     * Detects ruptures and quality decline patterns.
     */
    public void afterWeeklyCheck(TrackingSheet sheet, WeeklyCheck wc) {
        try {
            if (wc.getResults() != null) {
                for (ArracheResult r : wc.getResults()) {
                    if (r.getNokReasons() != null && r.getNokReasons().isRupture()) {
                        onRuptureDetected(sheet.getValiseNumber(), r.getPositionInValise(),
                                wc.getSignedBy(), "hebdomadaire");
                    }
                }
            }
            checkAndNotifyPattern(sheet);
        } catch (Exception e) {
            log.warn("Notification dispatch failed (weekly): {}", e.getMessage(), e);
        }
    }

    public void afterMonthlyCheck(TrackingSheet sheet, TrackingSheet.MonthlyCheck mc) {
        try {
            if (mc.getResults() != null) {
                for (ArracheResult r : mc.getResults()) {
                    if (r.getNokReasons() != null && r.getNokReasons().isRupture()) {
                        onRuptureDetected(sheet.getValiseNumber(), r.getPositionInValise(),
                                mc.getSignedBy(), "mensuel");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Notification dispatch failed (monthly): {}", e.getMessage(), e);
        }
    }

    public void afterQuarterlyVerdict(TrackingSheet sheet, TrackingSheet.QuarterlyVerdict qv) {
        try {
            int nokCount = 0;
            if (qv.getResults() != null) {
                for (ArracheResult r : qv.getResults()) {
                    if ("NOK".equals(r.getResult())) nokCount++;
                }
            }
            if (nokCount > 0) {
                onQuarterlyVerdictNok(sheet.getValiseId(), sheet.getValiseNumber(), nokCount);
            }
        } catch (Exception e) {
            log.warn("Notification dispatch failed (quarterly): {}", e.getMessage(), e);
        }
    }

    /** Simple count of how many users will be on the receiving end (for tests/debug). */
    @SuppressWarnings("unused")
    private long countUsersInRole(String role) {
        return userRepository.findAll().stream()
                .filter(u -> role.equals(u.getRole()) && u.isActive())
                .count();
    }

    @SuppressWarnings("unused")
    private boolean hasActiveUserForRole(String role) {
        return userRepository.findAll().stream()
                .anyMatch(u -> role.equals(u.getRole()) && u.isActive() && Boolean.TRUE);
    }

    /** Exposed for the scheduler. */
    public void onAdminCriticalOverdue(String valiseNumber, int days) {
        onOverdueWeeklyCritical(valiseNumber, days);
    }

    /** Exposed for the scheduler. */
    public List<User> findActiveReparateurForValise(String valiseId) {
        return userRepository.findAll().stream()
                .filter(u -> valiseId.equals(u.getValiseId())
                          && "REPARATEUR".equals(u.getRole())
                          && u.isActive())
                .toList();
    }
}
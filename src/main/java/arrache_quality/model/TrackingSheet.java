package arrache_quality.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "tracking_sheets")
public class TrackingSheet {

    @Id
    private String id;

    private String valiseId;
    private String valiseNumber;
    private String pf;
    private String segment;
    private int quarter;
    private int year;

    private List<WeeklyCheck> weeklyChecks = new ArrayList<>();
    private List<MonthlyCheck> monthlyChecks = new ArrayList<>();
    private QuarterlyVerdict quarterlyVerdict;

    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Weekly check (Sem.) ──
    @Data
    public static class WeeklyCheck {
        private int weekNumber;
        private LocalDateTime date;
        private String inspectorMatricule;
        private String signedBy;
        private List<ArracheResult> results = new ArrayList<>();
    }

    // ── Monthly check (QM OP.) ──
    @Data
    public static class MonthlyCheck {
        private int month;
        private int year;
        private LocalDateTime date;
        private String inspectorMatricule;
        private String signedBy;
        private List<ArracheResult> results = new ArrayList<>();
    }

    // ── Quarterly verdict (QM Labo) ──
    @Data
    public static class QuarterlyVerdict {
        private LocalDateTime date;
        private String labMatricule;
        private String signedBy;
        private List<ArracheResult> results = new ArrayList<>();
    }

    // ── One result per arrache per inspection ──
    @Data
    public static class ArracheResult {
        private String arracheId;
        private int positionInValise;
        private String result; // "OK" or "NOK"
        private NokReasons nokReasons = new NokReasons();
    }

    // ── The 3 NOK criteria from the paper sheet ──
    @Data
    public static class NokReasons {
        private boolean bavure = false;        // ① Présence de bavure
        private boolean deformation = false;   // ② Présence de déformation
        private boolean rupture = false;       // ③ Présence de rupture
    }
}
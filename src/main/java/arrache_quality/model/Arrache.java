package arrache_quality.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "arraches")
public class Arrache {

    @Id
    private String id;

    private String valiseId;

    @Indexed(unique = true)
    private String arracheNumber;

    private int positionInValise;
    private String toolDescription;
    private String status = "OPERATIONAL";
    private int totalNokCount = 0;
    private int consecutiveNokWeeks = 0;
    private boolean ruptureEverSeen = false;
    private int repairCount = 0;
    private double riskScore = 0.0;
    private LocalDateTime createdAt = LocalDateTime.now();
}
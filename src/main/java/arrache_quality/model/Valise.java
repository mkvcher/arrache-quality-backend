package arrache_quality.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "valises")
public class Valise {

    @Id
    private String id;

    @Indexed(unique = true)
    private String valiseNumber;

    private String pf;
    private String segment;
    private String assignedReparateurMatricule;
    private String location;
    private String status = "OK";
    private LocalDateTime createdAt = LocalDateTime.now();
}
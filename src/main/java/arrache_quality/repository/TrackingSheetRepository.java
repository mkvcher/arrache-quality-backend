package arrache_quality.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import arrache_quality.model.TrackingSheet;

@Repository
public interface TrackingSheetRepository extends MongoRepository<TrackingSheet, String> {
    
    Optional<TrackingSheet> findByValiseIdAndQuarterAndYear(
        String valiseId, int quarter, int year);
    
    List<TrackingSheet> findByValiseId(String valiseId);
}
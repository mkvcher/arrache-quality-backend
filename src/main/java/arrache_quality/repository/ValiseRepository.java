package arrache_quality.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import arrache_quality.model.Valise;

@Repository
public interface ValiseRepository extends MongoRepository<Valise, String> {
    Optional<Valise> findByValiseNumber(String valiseNumber);
}
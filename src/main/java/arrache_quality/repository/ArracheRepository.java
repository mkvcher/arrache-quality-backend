package arrache_quality.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import arrache_quality.model.Arrache;

@Repository
public interface ArracheRepository extends MongoRepository<Arrache, String> {
    List<Arrache> findByValiseId(String valiseId);
}
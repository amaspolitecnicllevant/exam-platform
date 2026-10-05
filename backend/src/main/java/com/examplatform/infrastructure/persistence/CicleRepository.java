package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Cicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CicleRepository extends JpaRepository<Cicle, UUID> {
    List<Cicle> findByDepartamentIdOrderByCodiAsc(UUID departamentId);
    Optional<Cicle> findByCodi(String codi);
}

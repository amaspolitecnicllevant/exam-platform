package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.EquipAula;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipAulaRepository extends JpaRepository<EquipAula, UUID> {
    List<EquipAula> findByAulaIdOrderByNomAsc(UUID aulaId);
    Optional<EquipAula> findByAulaIdAndNom(UUID aulaId, String nom);
    long countByAulaId(UUID aulaId);
}

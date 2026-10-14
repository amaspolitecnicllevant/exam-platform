package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.UnitatTreball;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnitatTreballRepository extends JpaRepository<UnitatTreball, UUID> {
    List<UnitatTreball> findByModulIdOrderByOrdreAsc(UUID modulId);
}

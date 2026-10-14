package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Competencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompetenciaRepository extends JpaRepository<Competencia, UUID> {
    List<Competencia> findByModulIdOrderByOrdreAsc(UUID modulId);
}

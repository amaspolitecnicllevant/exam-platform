package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.ProfessorDepartament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProfessorDepartamentRepository
        extends JpaRepository<ProfessorDepartament, ProfessorDepartament.PK> {

    List<ProfessorDepartament> findByProfessorId(UUID professorId);

    List<ProfessorDepartament> findByDepartamentId(UUID departamentId);

    boolean existsByProfessorIdAndDepartamentId(UUID professorId, UUID departamentId);

    void deleteByProfessorIdAndDepartamentId(UUID professorId, UUID departamentId);
}

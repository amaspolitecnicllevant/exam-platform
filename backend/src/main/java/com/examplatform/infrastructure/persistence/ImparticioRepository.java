package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Imparticio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ImparticioRepository extends JpaRepository<Imparticio, UUID> {
    List<Imparticio> findByProfessorId(UUID professorId);
    List<Imparticio> findByModulIdAndCurs(UUID modulId, String curs);
    boolean existsByProfessorIdAndModulIdAndCurs(UUID professorId, UUID modulId, String curs);

    @Query("SELECT COUNT(i) > 0 FROM Imparticio i WHERE i.professor.id = :professorId AND i.modul.id = :modulId")
    boolean professorImparteixModul(@Param("professorId") UUID professorId,
                                   @Param("modulId") UUID modulId);
}

package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.ExamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ExamRepository extends JpaRepository<Exam, UUID> {
    List<Exam> findByCreatedById(UUID professorId);

    /** Exàmens que un professor pot gestionar: els que ha creat i els dels mòduls que imparteix. */
    @Query("SELECT e FROM Exam e WHERE e.createdBy.id = :professorId OR e.modul.id IN "
            + "(SELECT i.modul.id FROM Imparticio i WHERE i.professor.id = :professorId)")
    List<Exam> findGestionablesPer(@Param("professorId") UUID professorId);
    List<Exam> findByStatus(ExamStatus status);

    boolean existsByCreatedById(UUID userId);

    @Query("SELECT e FROM Exam e WHERE e.status = 'DRAFT' AND e.scheduledAt IS NOT NULL AND e.scheduledAt <= :now AND e.scheduledGrup IS NOT NULL")
    List<Exam> findDueForActivation(@Param("now") LocalDateTime now);

    @Query("SELECT e FROM Exam e WHERE e.scheduledGrup.id = :grupId AND e.id != :examId AND e.scheduledAt IS NOT NULL AND e.status <> 'CLOSED'")
    List<Exam> findScheduledForGrup(@Param("grupId") UUID grupId, @Param("examId") UUID examId);

    @Query("""
            SELECT DISTINCT e FROM Exam e
            LEFT JOIN Matricula mt ON mt.modul = e.modul AND mt.alumne.id = :alumneId
            LEFT JOIN ExamSession s ON s.exam = e AND s.student.id = :alumneId
            WHERE e.status = 'PUBLISHED'
              AND (
                (e.scheduledAt IS NULL AND e.restringit = false AND mt.id IS NOT NULL)
                OR s.id IS NOT NULL
              )
            """)
    List<Exam> findPublishedForStudent(@Param("alumneId") UUID alumneId);

    // Exàmens PUBLISHED on scheduledAt + durada(min) + gracePeriod(min) < ara
    @Query("""
            SELECT e FROM Exam e
            WHERE e.status = 'PUBLISHED'
              AND e.scheduledAt IS NOT NULL
              AND e.scheduledAt < :cutoff
            """)
    List<Exam> findExpiredPublished(@Param("cutoff") LocalDateTime cutoff);
}

package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.ExamSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamSessionRepository extends JpaRepository<ExamSession, UUID> {
    Optional<ExamSession> findByExamIdAndStudentId(UUID examId, UUID studentId);
    List<ExamSession> findByExamId(UUID examId);
    List<ExamSession> findByStudentId(UUID studentId);

    @Query("SELECT s FROM ExamSession s JOIN FETCH s.student JOIN FETCH s.exam LEFT JOIN FETCH s.answers WHERE s.exam.id = :examId")
    List<ExamSession> findByExamIdWithDetails(UUID examId);

    @Query("SELECT s FROM ExamSession s JOIN FETCH s.exam e LEFT JOIN FETCH e.modul WHERE s.student.id = :studentId ORDER BY s.startedAt DESC")
    List<ExamSession> findByStudentIdWithExam(@Param("studentId") UUID studentId);

    @Query("SELECT s.student.id FROM ExamSession s WHERE s.exam.id = :examId")
    List<UUID> findStudentIdsByExamId(@Param("examId") UUID examId);

    boolean existsByStudentId(UUID studentId);
    boolean existsByExamId(UUID examId);

    @Query(value = """
            SELECT s.* FROM exam_sessions s
            JOIN exams e ON s.exam_id = e.id
            WHERE s.status = 'IN_PROGRESS'
              AND s.started_at + (e.durada * INTERVAL '1 minute') + (:graciaSegons * INTERVAL '1 second') < NOW()
            """, nativeQuery = true)
    List<ExamSession> findExpiredInProgress(@Param("graciaSegons") int graciaSegons);

    /** Sessions en curs amb el temps exhaurit (sense marge). */
    default List<ExamSession> findExpiredInProgress() {
        return findExpiredInProgress(0);
    }
}

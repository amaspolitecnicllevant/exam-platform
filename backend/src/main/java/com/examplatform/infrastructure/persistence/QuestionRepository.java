package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
    List<Question> findByExamIdOrderByOrdreAsc(UUID examId);

    /** Preguntes dels exàmens d'un mòdul que treballen una competència. */
    @Query("SELECT COUNT(q) FROM Question q WHERE q.exam.modul.id = :modulId AND q.competencia = :codi")
    long comptaCompetencia(@Param("modulId") UUID modulId, @Param("codi") String codi);
}

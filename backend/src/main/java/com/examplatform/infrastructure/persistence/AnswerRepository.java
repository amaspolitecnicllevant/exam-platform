package com.examplatform.infrastructure.persistence;

import com.examplatform.domain.model.Answer;
import com.examplatform.domain.model.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {
    List<Answer> findBySessionId(UUID sessionId);
    Optional<Answer> findBySessionIdAndQuestionId(UUID sessionId, UUID questionId);
    List<Answer> findBySessionIdIn(List<UUID> sessionIds);
    List<Answer> findByQuestionId(UUID questionId);
    void deleteBySessionId(UUID sessionId);

    /**
     * Respostes de sessions entregades que el professor encara no ha revisat
     * (sense nota manual), excloent-ne les preguntes anul·lades i els tipus indicats.
     */
    @Query("""
            select a from Answer a
            where a.session.exam.id = :examId
              and a.session.status = com.examplatform.domain.model.SessionStatus.SUBMITTED
              and a.manualScore is null
              and a.question.anulada = false
              and a.question.tipus not in :exclosos
            """)
    List<Answer> findPendentsRevisio(@Param("examId") UUID examId,
                                     @Param("exclosos") Collection<QuestionType> exclosos);

    /** Ids de les respostes amb contingut d'una sessió per a preguntes dels tipus indicats. */
    @Query("""
            select a.id from Answer a
            where a.session.id = :sessionId
              and a.question.tipus in :tipus
              and a.contingut is not null
            """)
    List<UUID> findIdsAmbContingut(@Param("sessionId") UUID sessionId,
                                   @Param("tipus") Collection<QuestionType> tipus);
}

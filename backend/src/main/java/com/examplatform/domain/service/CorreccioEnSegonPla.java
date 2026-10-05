package com.examplatform.domain.service;

import com.examplatform.domain.model.QuestionType;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Executa les respostes de codi d'una sessió quan s'entrega, perquè la nota proposada
 * ja hi sigui quan el professor obre la correcció. Corre en un pool limitat
 * ({@code correction.async-threads}) per no saturar Docker quan entreguen molts alumnes alhora.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorreccioEnSegonPla {

    private static final List<QuestionType> EXECUTABLES =
            Arrays.stream(QuestionType.values()).filter(QuestionType::isExecutable).toList();

    private final AnswerRepository answerRepository;
    private final CorrectionService correctionService;

    @Async("correctionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessioEntregada(SessioEntregadaEvent event) {
        executa(event.sessionId());
    }

    void executa(UUID sessionId) {
        for (UUID answerId : answerRepository.findIdsAmbContingut(sessionId, EXECUTABLES)) {
            try {
                correctionService.execute(answerId);
            } catch (RuntimeException e) {
                // Una resposta que falla no ha d'impedir corregir la resta
                log.warn("No s'ha pogut corregir la resposta {}: {}", answerId, e.getMessage());
            }
        }
    }
}

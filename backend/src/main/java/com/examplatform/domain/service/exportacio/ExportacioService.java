package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.Answer;
import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.ExamSession;
import com.examplatform.domain.model.User;
import com.examplatform.domain.service.ExamService;
import com.examplatform.domain.service.StatsService;
import com.examplatform.dto.ExamStatsDto;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Carrega les dades d'un examen per exportar-les, comprovant que l'usuari el pot gestionar. */
@Service
@RequiredArgsConstructor
public class ExportacioService {

    private final ExamService examService;
    private final ExamSessionRepository sessionRepository;
    private final StatsService statsService;

    @Transactional(readOnly = true)
    public DadesExamen dades(UUID examId, User usuari) {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, usuari);
        List<ExamSession> sessions = sessionRepository.findByExamIdWithDetails(examId);
        List<Answer> respostes = sessions.stream().flatMap(s -> s.getAnswers().stream()).toList();
        return DadesExamen.de(exam, sessions, respostes);
    }

    @Transactional(readOnly = true)
    public ExamStatsDto estadistiques(UUID examId, User usuari) {
        return statsService.stats(examId, usuari);
    }

    /** Pla del ZIP de lliuraments; es calcula amb la transacció oberta i el ZIP s'escriu després. */
    @Transactional(readOnly = true)
    public ExportacioFitxers.Pla plaFitxers(UUID examId, User usuari) {
        return ExportacioFitxers.pla(dades(examId, usuari), LocalDate.now());
    }
}

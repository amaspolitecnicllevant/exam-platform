package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.domain.service.copies.DeteccioCopies;
import com.examplatform.domain.service.copies.DeteccioCopies.Parametres;
import com.examplatform.dto.CopiesInformeDto;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Informe de possibles còpies d'un examen (punt 2.3 de la Fase 3). */
@Service
@RequiredArgsConstructor
public class CopiesService {

    private final ExamService examService;
    private final ExamSessionRepository sessionRepository;
    private final AnswerRepository answerRepository;
    private final ConfiguracioService configuracioService;

    @Transactional(readOnly = true)
    public CopiesInformeDto informe(UUID examId, User user) {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, user);

        List<ExamSession> entregades = sessionRepository.findByExamIdWithDetails(examId).stream()
                .filter(s -> s.getStatus() == SessionStatus.SUBMITTED).toList();
        Map<UUID, String> noms = new HashMap<>();
        entregades.forEach(s -> noms.put(s.getId(), s.getStudent().getName()));

        Map<UUID, List<DeteccioCopies.Resposta>> perPregunta = new HashMap<>();
        if (!entregades.isEmpty()) {
            for (Answer a : answerRepository.findBySessionIdIn(entregades.stream().map(ExamSession::getId).toList())) {
                perPregunta.computeIfAbsent(a.getQuestion().getId(), k -> new ArrayList<>())
                        .add(new DeteccioCopies.Resposta(a.getSession().getId(), a.getContingut()));
            }
        }

        Map<UUID, Question> preguntesPerId = new HashMap<>();
        List<DeteccioCopies.Pregunta> preguntes = exam.getQuestions().stream()
                .filter(q -> q.getTipus() != QuestionType.SECTION)
                .sorted(Comparator.comparingInt(Question::getOrdre))
                .peek(q -> preguntesPerId.put(q.getId(), q))
                .map(q -> new DeteccioCopies.Pregunta(q.getId(), q.getOrdre(), q.getTipus(), q.getEnunciat(),
                        q.getModelResposta(), q.getCorrectChoice(), q.isAnulada(), q.isAmbApunts(),
                        perPregunta.getOrDefault(q.getId(), List.of())))
                .toList();

        ConfiguracioSistema config = configuracioService.get();
        Parametres par = Parametres.perDefecte(config.getCopiesLlindar(), config.getCopiesLlindarApunts());
        List<CopiesInformeDto.Parella> parelles = DeteccioCopies.analitza(preguntes, par).stream()
                .map(p -> new CopiesInformeDto.Parella(
                        p.sessioA(), noms.get(p.sessioA()),
                        p.sessioB(), noms.get(p.sessioB()),
                        p.maxSemblanca(),
                        p.coincidencies().stream().map(c -> new CopiesInformeDto.Coincidencia(
                                c.preguntaId(), c.ordre(), c.tipus(), c.ambApunts(), preguntesPerId.get(c.preguntaId()).getEnunciat(),
                                c.semblanca(), c.respostaA(), c.respostaB(),
                                c.marquesA().stream().map(m -> new int[]{m.inici(), m.fi()}).toList(),
                                c.marquesB().stream().map(m -> new int[]{m.inici(), m.fi()}).toList())).toList(),
                        p.erradesTestComunes()))
                .toList();

        return new CopiesInformeDto(par.llindarPercent(), par.llindarApuntsPercent(), par.minErradesTest(),
                entregades.size(), parelles);
    }
}

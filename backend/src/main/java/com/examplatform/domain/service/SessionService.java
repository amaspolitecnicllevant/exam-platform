package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.*;
import com.examplatform.infrastructure.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import com.examplatform.util.CidrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SessionService {


    private final ExamSessionRepository sessionRepository;
    private final AnswerRepository answerRepository;
    private final ExamService examService;
    private final MatriculaRepository matriculaRepository;
    private final CorrectionService correctionService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.examplatform.infrastructure.persistence.GrupRepository grupRepository;

    @Value("${exam.access-window-minutes:20}")
    private int accessWindowMinutes;

    /** Temps mínim que té un alumne quan el professor li reprèn la sessió. */
    @Value("${exam.reset-min-minutes:10}")
    private int resetMinMinutes = 10;

    /** Marge després del final del temps en què encara s'accepten desaments (latència de xarxa). */
    @Value("${exam.save-grace-seconds:60}")
    private int saveGraceSeconds = 60;

    /** Només es pot fer (començar, desar, executar) un examen actiu: ni desactivat ni tancat. */
    public static void assertExamenActiu(Exam exam) {
        if (exam.getStatus() == ExamStatus.CLOSED) {
            throw new IllegalStateException("L'examen s'ha tancat: ja no es poden desar respostes");
        }
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("L'examen no està actiu: el professor l'ha desactivat");
        }
    }

    private void assertDinsDelTemps(ExamSession session) {
        if (session.getStartedAt() == null) {
            throw new IllegalStateException("Encara no has començat l'examen");
        }
        LocalDateTime limit = session.getStartedAt()
                .plusMinutes(session.getExam().getDurada())
                .plusSeconds(saveGraceSeconds);
        if (LocalDateTime.now().isAfter(limit)) {
            throw new IllegalStateException("S'ha acabat el temps de l'examen");
        }
    }

    @Transactional
    public SessionDto startOrResume(UUID examId, User student, String clientIp) {
        return sessionRepository.findByExamIdAndStudentId(examId, student.getId())
                .map(s -> {
                    if (s.getStatus() == SessionStatus.IN_PROGRESS) assertExamenActiu(s.getExam());
                    assertAulaPermesa(s.getExam(), clientIp);
                    if (s.getStartedAt() == null) {
                        // Sessió creada per avançat: el rellotge comença ara
                        s.setStartedAt(iniciRellotge(s.getExam()));
                        s.setClientIp(clientIp);
                        return toDto(sessionRepository.save(s));
                    }
                    return toDto(s);
                })
                .orElseGet(() -> {
                    Exam exam = examService.getEntity(examId);
                    if (exam.getStatus() != ExamStatus.PUBLISHED) {
                        throw new IllegalStateException("L'examen no està publicat");
                    }
                    assertFinestra(exam);
                    assertAulaPermesa(exam, clientIp);
                    assertMatriculaPermesa(exam, student);
                    assertGrupPermes(exam, student);
                    ExamSession session = ExamSession.builder()
                            .exam(exam).student(student).clientIp(clientIp)
                            .startedAt(iniciRellotge(exam)).build();
                    return toDto(sessionRepository.save(session));
                });
    }

    /**
     * Quan el professor reprèn una sessió entregada, el rellotge es comporta com si s'hagués aturat
     * en entregar: l'alumne continua amb el temps que li quedava, i com a mínim
     * {@code exam.reset-min-minutes} (per si va entregar per error al final). Sense això el
     * planificador la tornaria a entregar de seguida.
     */
    private LocalDateTime iniciEnReprendre(ExamSession session, Exam exam) {
        java.time.Duration durada = java.time.Duration.ofMinutes(exam.getDurada());
        LocalDateTime finalUs = session.getSubmittedAt() != null ? session.getSubmittedAt() : LocalDateTime.now();
        java.time.Duration restant = durada.minus(java.time.Duration.between(session.getStartedAt(), finalUs));
        java.time.Duration minim = java.time.Duration.ofMinutes(Math.min(resetMinMinutes, exam.getDurada()));
        if (restant.compareTo(minim) < 0) restant = minim;
        return LocalDateTime.now().minus(durada).plus(restant);
    }

    /**
     * Moment des del qual compta el temps de l'alumne: ara, o l'hora programada si l'examen és
     * programat i ja ha començat (així té una hora de final comuna per a tothom).
     */
    private static LocalDateTime iniciRellotge(Exam exam) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime programat = exam.getScheduledAt();
        return programat != null && programat.isBefore(now) ? programat : now;
    }

    private void assertFinestra(Exam exam) {
        if (exam.getScheduledAt() == null) return;
        LocalDateTime now   = LocalDateTime.now();
        LocalDateTime start = exam.getScheduledAt();
        LocalDateTime end   = start.plusMinutes(accessWindowMinutes);
        if (now.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "L'examen no ha començat encara. Comença a les " + com.examplatform.util.HoraLocal.format(start, "HH:mm") + ".");
        }
        if (now.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El període d'accés ha tancat (finestra: " + accessWindowMinutes + " min des de les " + com.examplatform.util.HoraLocal.format(start, "HH:mm") + ").");
        }
    }

    private void assertMatriculaPermesa(Exam exam, User student) {
        if (exam.getModul() == null) return;
        if (!matriculaRepository.existsByAlumneIdAndModulId(student.getId(), exam.getModul().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No estàs matriculat al mòdul d'aquest examen");
        }
    }

    /**
     * Un examen programat per a un grup només el poden començar els alumnes del grup (en activar-se
     * ja se'ls crea la sessió; això cobreix els afegits després). Sense aquesta comprovació, un altre
     * alumne del mòdul hi podria entrar amb l'enllaç directe.
     */
    private void assertGrupPermes(Exam exam, User student) {
        if (exam.getScheduledGrup() == null) return;
        if (!grupRepository.teAlumne(exam.getScheduledGrup().getId(), student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Aquest examen és només per als alumnes del grup " + exam.getScheduledGrup().getName());
        }
    }

    private void assertAulaPermesa(Exam exam, String clientIp) {
        if (exam.getAula() == null) return;
        if (!CidrUtil.isInCidr(clientIp, exam.getAula().getXarxaCidr())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Aquest examen només es pot fer des de l'aula " + exam.getAula().getNom()
                    + " (" + exam.getAula().getXarxaCidr() + ")");
        }
    }

    @Transactional
    public AnswerDto saveAnswer(UUID sessionId, AnswerDto.SaveRequest req, User student, String clientIp) {
        ExamSession session = getSessionOwned(sessionId, student);
        if (session.getStatus() == SessionStatus.SUBMITTED) {
            throw new IllegalStateException("La sessió ja ha estat enviada");
        }
        assertExamenActiu(session.getExam());
        // L'aula es comprova a cada desament: no es pot començar a classe i continuar des de fora
        assertAulaPermesa(session.getExam(), clientIp);
        assertDinsDelTemps(session);

        Answer answer = answerRepository
                .findBySessionIdAndQuestionId(sessionId, req.questionId())
                .orElseGet(() -> Answer.builder().session(session)
                        .question(findQuestion(session, req.questionId())).build());

        answer.setContingut(req.contingut());
        return AnswerDto.senseNotes(answerRepository.save(answer));
    }

    @Transactional
    public void recordFocusLoss(UUID sessionId, User student) {
        ExamSession session = getSessionOwned(sessionId, student);
        if (session.getStatus() == SessionStatus.SUBMITTED) return;
        session.setFocusLossCount(session.getFocusLossCount() + 1);
        sessionRepository.save(session);
    }

    @Transactional
    public SessionDto submit(UUID sessionId, User student) {
        ExamSession session = getSessionOwned(sessionId, student);
        if (session.getStatus() == SessionStatus.SUBMITTED) {
            throw new IllegalStateException("La sessió ja ha estat enviada");
        }
        session.setStatus(SessionStatus.SUBMITTED);
        session.setSubmittedAt(LocalDateTime.now());
        corregeixEnEntregar(session);
        return toDto(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<MonitorDto> monitor(UUID examId, User requestingUser) {
        // Mateixa regla que per modificar l'examen: creador, professors del mòdul o admin
        examService.assertOwnership(examService.getEntity(examId), requestingUser);
        return sessionRepository.findByExamIdWithDetails(examId).stream()
                .map(MonitorDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SessionDto> findByExam(UUID examId, User requestingUser) {
        examService.assertOwnership(examService.getEntity(examId), requestingUser);
        return sessionRepository.findByExamIdWithDetails(examId).stream()
                .map(s -> {
                    List<AnswerDto> answers = answerRepository.findBySessionId(s.getId())
                            .stream().map(AnswerDto::from).toList();
                    return SessionDto.from(s, answers);
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<SessionDto> findMySubmitted(UUID studentId) {
        return sessionRepository.findByStudentIdWithExam(studentId).stream()
                .filter(s -> s.getStatus() == com.examplatform.domain.model.SessionStatus.SUBMITTED)
                .map(s -> SessionDto.from(s, List.of()))
                .toList();
    }

    /**
     * Historial de l'alumne: exàmens entregats, del més antic al més recent, amb la nota només
     * quan s'ha publicat (calculada amb la mateixa regla que la correcció).
     */
    @Transactional(readOnly = true)
    public List<HistorialDto> historial(User student) {
        List<ExamSession> entregades = sessionRepository.findByStudentIdWithExam(student.getId()).stream()
                .filter(s -> s.getStatus() == SessionStatus.SUBMITTED)
                .sorted(Comparator.comparing(ExamSession::getSubmittedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();

        List<UUID> ambNotes = entregades.stream()
                .filter(s -> s.getExam().isNotesVisibles()).map(ExamSession::getId).toList();
        Map<UUID, Map<UUID, Answer>> respostes = new HashMap<>();
        if (!ambNotes.isEmpty()) {
            for (Answer a : answerRepository.findBySessionIdIn(ambNotes)) {
                respostes.computeIfAbsent(a.getSession().getId(), k -> new HashMap<>())
                        .put(a.getQuestion().getId(), a);
            }
        }

        return entregades.stream().map(s -> {
            Exam exam = s.getExam();
            BigDecimal nota = null;
            if (exam.isNotesVisibles()) {
                List<Question> preguntes = exam.getQuestions().stream()
                        .filter(q -> q.getTipus() != QuestionType.SECTION).toList();
                // Sobre 10, per poder comparar exàmens de puntuacions diferents
                nota = Puntuacio.notaSobreDeu(preguntes, respostes.getOrDefault(s.getId(), Map.of()));
            }
            return new HistorialDto(s.getId(), exam.getId(), exam.getTitle(),
                    exam.getModul() != null ? exam.getModul().getId() : null,
                    exam.getModul() != null ? exam.getModul().getNom() : null,
                    s.getSubmittedAt() != null ? s.getSubmittedAt().toInstant(java.time.ZoneOffset.UTC) : null,
                    exam.isNotesVisibles(), nota);
        }).toList();
    }

    @Transactional(readOnly = true)
    public SessionDto findById(UUID sessionId, User requestingUser) {
        ExamSession s = getSession(sessionId);
        // L'alumne només pot veure la seva pròpia sessió
        if (requestingUser.getRole() == com.examplatform.domain.model.Role.STUDENT) {
            if (!s.getStudent().getId().equals(requestingUser.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tens accés a aquesta sessió");
            }
        } else {
            // Professor: només sessions d'exàmens que pot gestionar
            examService.assertOwnership(s.getExam(), requestingUser);
        }
        boolean amagaNotes = requestingUser.getRole() == com.examplatform.domain.model.Role.STUDENT
                && !s.getExam().isNotesVisibles();
        List<AnswerDto> answers = answerRepository.findBySessionId(sessionId)
                .stream().map(a -> amagaNotes ? AnswerDto.senseNotes(a) : AnswerDto.from(a)).toList();
        return SessionDto.from(s, answers);
    }

    /**
     * Entrega les sessions en curs d'un examen (en tancar-lo): sense això, els alumnes podien
     * continuar fins a exhaurir el temps. Les que no s'han començat es queden com a no presentades.
     *
     * @return nombre de sessions entregades
     */
    @Transactional
    public int entregaEnCurs(UUID examId) {
        int n = 0;
        for (ExamSession s : sessionRepository.findByExamIdWithDetails(examId)) {
            if (s.getStatus() == SessionStatus.IN_PROGRESS && s.getStartedAt() != null) {
                s.setStatus(SessionStatus.SUBMITTED);
                s.setSubmittedAt(LocalDateTime.now());
                corregeixEnEntregar(s);
                sessionRepository.save(s);
                n++;
            }
        }
        return n;
    }

    @Transactional
    public void forceSubmit(UUID sessionId) {
        ExamSession session = getSession(sessionId);
        if (session.getStatus() == SessionStatus.SUBMITTED) return;
        session.setStatus(SessionStatus.SUBMITTED);
        session.setSubmittedAt(LocalDateTime.now());
        corregeixEnEntregar(session);
        sessionRepository.save(session);
    }

    /**
     * Torna a corregir les respostes d'una pregunta de test (p. ex. si canvia la resposta correcta).
     * El bonus no toca cap nota: els punts sencers s'apliquen en calcular la nota
     * ({@link Puntuacio}), de manera que treure el bonus recupera les notes anteriors.
     *
     * @return nombre de respostes re-corregides
     */
    /** Torna a corregir totes les preguntes de test d'un examen (p. ex. si canvia la penalització). */
    @Transactional
    public int reCorregeixTest(UUID examId) {
        return examService.getEntity(examId).getQuestions().stream().mapToInt(this::reCorrectQuestion).sum();
    }

    @Transactional
    public int reCorrectQuestion(Question question) {
        if (question.getTipus() != QuestionType.CHOICE) return 0;
        BigDecimal factor = question.getExam().getPenalitzacioChoice();
        int n = 0;
        for (Answer answer : answerRepository.findByQuestionId(question.getId())) {
            answer.setAutoScore(scoreChoice(answer.getContingut(), question.getCorrectChoice(),
                    question.getPunts(), factor));
            answerRepository.save(answer);
            n++;
        }
        return n;
    }

    /**
     * Correcció en entregar: el test es corregeix al moment, les preguntes de text amb
     * conceptes clau reben una proposta, i les de codi s'executen en segon pla un cop confirmada
     * la transacció.
     */
    private void corregeixEnEntregar(ExamSession session) {
        autoCorrectChoices(session);
        correctionService.proposaSenseExecucio(session);
        eventPublisher.publishEvent(new SessioEntregadaEvent(session.getId()));
    }

    private void autoCorrectChoices(ExamSession session) {
        BigDecimal factor = session.getExam().getPenalitzacioChoice();
        session.getExam().getQuestions().stream()
                .filter(q -> q.getTipus() == QuestionType.CHOICE)
                .forEach(q -> answerRepository
                        .findBySessionIdAndQuestionId(session.getId(), q.getId())
                        .ifPresent(answer -> {
                            answer.setAutoScore(scoreChoice(answer.getContingut(),
                                    q.getCorrectChoice(), q.getPunts(), factor));
                            answerRepository.save(answer);
                        }));
    }

    private BigDecimal scoreChoice(String given, String correct, BigDecimal punts, BigDecimal penalFactor) {
        String g = given   != null ? given.strip().toLowerCase()   : "";
        String c = correct != null ? correct.strip().toLowerCase() : "";
        if (g.isEmpty()) return BigDecimal.ZERO; // sense resposta: no penalitza
        if (g.equals(c)) return punts;
        if (penalFactor != null && penalFactor.compareTo(BigDecimal.ZERO) > 0) {
            return punts.multiply(penalFactor).negate()
                    .setScale(4, java.math.RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    @Transactional
    /**
     * L'alumne torna a fer un examen entregat: s'esborren les respostes i continua amb el temps
     * que li quedava (el rellotge no es reinicia). Només mentre l'examen és obert, les notes no
     * s'han publicat i des de l'aula, si en té.
     */
    public SessionDto restartByStudent(UUID examId, User student, String clientIp) {
        ExamSession session = sessionRepository.findByExamIdAndStudentId(examId, student.getId())
                .orElseThrow(() -> new NoSuchElementException("Sessió no trobada"));
        if (session.getStatus() != SessionStatus.SUBMITTED) {
            throw new IllegalStateException("Només es pot reiniciar una sessió ja enviada");
        }
        Exam exam = session.getExam();
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("L'examen ja està tancat: no es pot tornar a fer");
        }
        if (exam.isNotesVisibles()) {
            throw new IllegalStateException("Les notes ja s'han publicat: no es pot tornar a fer l'examen");
        }
        assertAulaPermesa(exam, clientIp);
        if (session.getStartedAt() == null
                || !LocalDateTime.now().isBefore(session.getStartedAt().plusMinutes(exam.getDurada()))) {
            throw new IllegalStateException("S'ha acabat el temps de l'examen: no es pot tornar a fer");
        }
        answerRepository.deleteBySessionId(session.getId());
        session.getAnswers().clear();
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setSubmittedAt(null);
        return SessionDto.from(sessionRepository.save(session), List.of());
    }

    @Transactional
    public SessionDto resetSession(UUID sessionId, User professor) {
        ExamSession session = getSession(sessionId);
        Exam exam = session.getExam();
        examService.assertOwnership(exam, professor);
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("Només es pot reprendre una sessió d'un examen actiu");
        }
        if (session.getStatus() == SessionStatus.SUBMITTED && session.getStartedAt() != null) {
            session.setStartedAt(iniciEnReprendre(session, exam));
        }
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setSubmittedAt(null);
        return toDto(sessionRepository.save(session));
    }

    private ExamSession getSessionOwned(UUID sessionId, User student) {
        ExamSession s = getSession(sessionId);
        if (!s.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tens accés a aquesta sessió");
        }
        return s;
    }

    private ExamSession getSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Sessió no trobada: " + id));
    }

    private Question findQuestion(ExamSession session, UUID questionId) {
        return session.getExam().getQuestions().stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Pregunta no trobada: " + questionId));
    }

    /** DTO per a l'alumne: les notes només hi són si el professor les ha publicat. */
    private SessionDto toDto(ExamSession s) {
        boolean notesVisibles = s.getExam().isNotesVisibles();
        List<AnswerDto> answers = answerRepository.findBySessionId(s.getId())
                .stream().map(a -> notesVisibles ? AnswerDto.from(a) : AnswerDto.senseNotes(a)).toList();
        return SessionDto.from(s, answers);
    }
}

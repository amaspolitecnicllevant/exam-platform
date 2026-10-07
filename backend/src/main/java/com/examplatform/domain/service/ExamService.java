package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.domain.port.ExamParser;
import com.examplatform.dto.ExamDto;
import com.examplatform.dto.ExamSettingsRequest;
import com.examplatform.dto.QuestionFileDto;
import com.examplatform.dto.QuestionPatchRequest;
import com.examplatform.dto.ScheduleRequest;
import com.examplatform.infrastructure.persistence.ExamRepository;
import com.examplatform.infrastructure.persistence.GrupRepository;
import com.examplatform.infrastructure.persistence.AulaRepository;
import com.examplatform.infrastructure.persistence.ImparticioRepository;
import com.examplatform.infrastructure.persistence.ModulRepository;
import com.examplatform.infrastructure.persistence.QuestionFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository    examRepository;
    private final ExamParser        examParser;
    private final GrupRepository    grupRepository;
    private final com.examplatform.infrastructure.persistence.QuestionRepository questionRepository;
    private final ModulRepository       modulRepository;
    private final ImparticioRepository  imparticioRepository;
    private final AulaRepository        aulaRepository;
    private final com.examplatform.infrastructure.persistence.ExamSessionRepository sessionRepository;
    private final QuestionFileRepository questionFileRepository;
    private final com.examplatform.infrastructure.persistence.AnswerRepository answerRepository;
    private final com.examplatform.infrastructure.storage.FitxersRespostaStorage fitxersStorage;

    /** Durada màxima d'un examen en minuts (8 hores). */
    static final int DURADA_MAXIMA = 480;


    @Transactional
    public ExamDto createFromMd(String mdContent, User professor) {
        Exam exam = examParser.parse(mdContent, professor);
        exam.getQuestions().forEach(q -> q.setExam(exam));
        return ExamDto.from(examRepository.save(exam), true);
    }

    @Transactional(readOnly = true)
    public List<ExamDto> findByProfessor(UUID professorId) {
        return examRepository.findByCreatedById(professorId).stream()
                .map(e -> ExamDto.from(e, true)).toList();
    }

    @Transactional(readOnly = true)
    public List<ExamDto> findPublishedForStudent(User student) {
        return examRepository.findPublishedForStudent(student.getId()).stream()
                .map(e -> ExamDto.from(e, false)).toList();
    }

    @Transactional
    public ExamDto assignModul(UUID examId, UUID modulId, User professor) {
        Exam exam = getEntity(examId);
        assertOwnership(exam, professor);
        Modul modul = modulRepository.findById(modulId)
                .orElseThrow(() -> new NoSuchElementException("Mòdul no trobat: " + modulId));
        if (professor.getRole() != Role.ADMIN
                && !imparticioRepository.professorImparteixModul(professor.getId(), modulId)) {
            throw new AccessDeniedException(
                    "No tens cap impartició per al mòdul " + modul.getCodi());
        }
        exam.setModul(modul);
        return ExamDto.from(examRepository.save(exam), true);
    }

    @Transactional
    public ExamDto assignAula(UUID examId, UUID aulaId, User professor) {
        Exam exam = getEntity(examId);
        assertOwnership(exam, professor);
        Aula aula = aulaRepository.findById(aulaId)
                .orElseThrow(() -> new NoSuchElementException("Aula no trobada: " + aulaId));
        exam.setAula(aula);
        return ExamDto.from(examRepository.save(exam), true);
    }

    @Transactional
    public ExamDto removeAula(UUID examId, User professor) {
        Exam exam = getEntity(examId);
        assertOwnership(exam, professor);
        exam.setAula(null);
        return ExamDto.from(examRepository.save(exam), true);
    }

    @Transactional(readOnly = true)
    /**
     * Les solucions (model, criteris, conceptes clau, resposta correcta) només les veu qui pot
     * gestionar l'examen; la resta (alumnes i altres professors) el rep com un alumne.
     */
    public ExamDto findById(UUID id, User requestingUser) {
        Exam exam = getEntity(id);
        boolean alumne = requestingUser.getRole() == com.examplatform.domain.model.Role.STUDENT;
        // Un alumne només veu exàmens publicats o els que ja ha fet (no esborranys d'altres)
        if (alumne && exam.getStatus() != ExamStatus.PUBLISHED
                && sessionRepository.findByExamIdAndStudentId(id, requestingUser.getId()).isEmpty()) {
            throw new NoSuchElementException("Examen no trobat: " + id);
        }
        boolean includeAnswers = !alumne && potGestionar(exam, requestingUser);
        Map<UUID, List<QuestionFileDto>> filesMap = exam.getQuestions().stream()
                .collect(Collectors.toMap(
                        Question::getId,
                        q -> questionFileRepository.findByQuestionId(q.getId()).stream()
                                .map(QuestionFileDto::from).toList()
                ));
        return ExamDto.from(exam, includeAnswers, filesMap);
    }

    @Transactional
    public ExamDto publish(UUID id, User requestingUser) {
        Exam exam = getEntity(id);
        assertOwnership(exam, requestingUser);
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new IllegalStateException("Només es pot publicar un examen en estat DRAFT");
        }
        // Com a la importació: un examen es puntua sobre 10 (l'editor permet canviar els punts de cada pregunta)
        BigDecimal total = exam.getQuestions().stream()
                .filter(q -> q.getTipus() != QuestionType.SECTION)
                .map(Question::getPunts)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.TEN) != 0) {
            throw new IllegalStateException("No es pot publicar: la suma de punts és "
                    + total.stripTrailingZeros().toPlainString() + " i ha de ser exactament 10");
        }
        exam.setStatus(ExamStatus.PUBLISHED);
        examRepository.save(exam);

        // Si l'examen té grup programat, crea les sessions pels alumnes que no en tenguin
        if (exam.getScheduledGrup() != null) {
            Grup grup = grupRepository.findByIdWithStudents(exam.getScheduledGrup().getId())
                    .orElse(exam.getScheduledGrup());
            Set<UUID> existing = new HashSet<>(sessionRepository.findStudentIdsByExamId(exam.getId()));
            grup.getStudents().forEach(student -> {
                if (!existing.contains(student.getId())) {
                    sessionRepository.save(ExamSession.builder().exam(exam).student(student).build());
                }
            });
        }

        return ExamDto.from(exam, true);
    }

    @Transactional
    public ExamDto unpublish(UUID id, User requestingUser) {
        Exam exam = getEntity(id);
        assertOwnership(exam, requestingUser);
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("Només es pot despublicar un examen PUBLISHED");
        }
        exam.setStatus(ExamStatus.DRAFT);
        return ExamDto.from(examRepository.save(exam), true);
    }

    @Transactional
    public ExamDto close(UUID id, User requestingUser) {
        Exam exam = getEntity(id);
        assertOwnership(exam, requestingUser);
        exam.setStatus(ExamStatus.CLOSED);
        return ExamDto.from(examRepository.save(exam), true);
    }

    @Transactional
    public ExamDto publicarNotes(UUID id, User requestingUser) {
        Exam exam = getEntity(id);
        assertOwnership(exam, requestingUser);
        int pendents = answerRepository
                .findPendentsRevisio(id, CorrectionService.PENDENTS_TIPUS_EXCLOSOS).size();
        if (pendents > 0) {
            throw new IllegalStateException("Hi ha " + pendents
                    + (pendents == 1 ? " resposta pendent" : " respostes pendents")
                    + " de revisar. Revisa-les o accepta les propostes abans de publicar les notes.");
        }
        exam.setNotesVisibles(true);
        return ExamDto.from(examRepository.save(exam), true);
    }

    @Transactional
    public ExamDto ocultarNotes(UUID id, User requestingUser) {
        Exam exam = getEntity(id);
        assertOwnership(exam, requestingUser);
        exam.setNotesVisibles(false);
        return ExamDto.from(examRepository.save(exam), true);
    }

    @Transactional
    public void delete(UUID id, User requestingUser) {
        Exam exam = getEntity(id);
        assertOwnership(exam, requestingUser);
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new IllegalStateException(
                    "Només es pot esborrar un examen en estat DRAFT (estat actual: " + exam.getStatus() + ")");
        }
        sessionRepository.findByExamId(id).forEach(s -> fitxersStorage.esborraSessio(s.getId()));
        examRepository.deleteById(id);
    }

    @Transactional
    public ExamDto schedule(UUID examId, ScheduleRequest req, User professor) {
        Exam exam = getEntity(examId);
        assertOwnership(exam, professor);
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new IllegalStateException("Només es pot programar un examen en estat DRAFT");
        }
        if (req.scheduledAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("La data de programació ha de ser futura");
        }
        Grup grup = grupRepository.findById(req.grupId())
                .orElseThrow(() -> new NoSuchElementException("Grup no trobat: " + req.grupId()));
        if (professor.getRole() != Role.ADMIN && !grup.getCreatedBy().getId().equals(professor.getId())) {
            throw new AccessDeniedException("El grup " + grup.getName() + " no és teu");
        }
        validateNoConflict(examId, req.grupId(), req.scheduledAt(), exam.getDurada());
        exam.setScheduledAt(req.scheduledAt());
        exam.setScheduledGrup(grup);
        return ExamDto.from(examRepository.save(exam), true);
    }

    @Transactional
    public ExamDto unschedule(UUID examId, User professor) {
        Exam exam = getEntity(examId);
        assertOwnership(exam, professor);
        exam.setScheduledAt(null);
        exam.setScheduledGrup(null);
        return ExamDto.from(examRepository.save(exam), true);
    }

    @Transactional
    public ExamDto reopenWindow(UUID examId, User professor) {
        Exam exam = getEntity(examId);
        assertOwnership(exam, professor);
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("Només es pot reobrir la finestra d'un examen publicat");
        }
        // Sense programar no hi ha finestra: els alumnes ja hi poden entrar. Posar-hi una hora
        // l'amagaria als que no l'han començat i el planificador el tancaria en acabar la durada
        if (exam.getScheduledAt() == null) {
            throw new IllegalStateException("Aquest examen no està programat: els alumnes ja hi poden entrar sense finestra d'accés");
        }
        exam.setScheduledAt(LocalDateTime.now());
        return ExamDto.from(examRepository.save(exam), true);
    }

    @Transactional
    /** Pregunta modificada i descripció dels canvis ("camp: abans → després"), per a l'auditoria. */
    public record ModificacioPregunta(Question pregunta, List<String> canvis) {}

    private static String siNo(boolean b) { return b ? "sí" : "no"; }

    public ModificacioPregunta patchQuestion(UUID examId, UUID questionId,
                                             QuestionPatchRequest req, User professor) {
        Exam exam = getEntity(examId);
        assertOwnership(exam, professor);
        Question q = questionRepository.findById(questionId)
                .filter(qu -> qu.getExam().getId().equals(examId))
                .orElseThrow(() -> new NoSuchElementException("Pregunta no trobada: " + questionId));
        List<String> canvis = new ArrayList<>();
        String correctaAbans = q.getCorrectChoice();
        boolean bonusAbans = q.isAnulada();
        String raAbans = q.getRa();
        String difAbans = q.getDificultat();
        boolean barrejaAbans = q.isBarrejarOpcions();
        boolean apuntsAbans = q.isAmbApunts();

        if (req.correctChoice() != null) {
            String choice = req.correctChoice().strip().toLowerCase();
            if (choice.isEmpty()) {
                throw new IllegalArgumentException("La resposta correcta no pot estar buida");
            }
            String lletra = choice.substring(0, 1);
            List<String> opcions = q.getChoices() == null ? List.of() : q.getChoices().lines()
                    .map(String::strip).filter(l -> !l.isEmpty())
                    .map(l -> l.substring(0, 1).toLowerCase()).toList();
            if (!opcions.contains(lletra)) {
                throw new IllegalArgumentException("La resposta correcta «" + lletra
                        + "» no és cap de les opcions (" + String.join(", ", opcions) + ")");
            }
            q.setCorrectChoice(lletra);
        }
        if (req.anulada() != null) {
            q.setAnulada(req.anulada());
        }
        if (req.ra() != null) {
            q.setRa(req.ra().isBlank() ? null : req.ra().strip());
        }
        if (req.dificultat() != null) {
            String dif = req.dificultat().strip().toLowerCase();
            q.setDificultat(dif.isBlank() ? null : dif);
        }
        if (req.barrejarOpcions() != null) {
            if (q.getTipus() != QuestionType.CHOICE) {
                throw new IllegalArgumentException("Només es poden barrejar les opcions de preguntes de test");
            }
            q.setBarrejarOpcions(req.barrejarOpcions());
        }
        if (req.ambApunts() != null) {
            q.setAmbApunts(req.ambApunts());
        }
        if (apuntsAbans != q.isAmbApunts())
            canvis.add("amb apunts: " + siNo(apuntsAbans) + " → " + siNo(q.isAmbApunts()));
        if (!Objects.equals(correctaAbans, q.getCorrectChoice()))
            canvis.add("resposta correcta: " + correctaAbans + " → " + q.getCorrectChoice());
        if (bonusAbans != q.isAnulada())
            canvis.add("bonus: " + siNo(bonusAbans) + " → " + siNo(q.isAnulada()));
        if (!Objects.equals(raAbans, q.getRa())) canvis.add("RA: " + raAbans + " → " + q.getRa());
        if (!Objects.equals(difAbans, q.getDificultat()))
            canvis.add("dificultat: " + difAbans + " → " + q.getDificultat());
        if (barrejaAbans != q.isBarrejarOpcions())
            canvis.add("barrejar opcions: " + siNo(barrejaAbans) + " → " + siNo(q.isBarrejarOpcions()));
        return new ModificacioPregunta(questionRepository.save(q), canvis);
    }

    @Transactional
    public ExamDto updateSettings(UUID examId, ExamSettingsRequest req, User professor) {
        Exam exam = getEntity(examId);
        assertOwnership(exam, professor);
        if (req.penalitzacioChoice() != null) {
            java.math.BigDecimal factor = req.penalitzacioChoice();
            if (factor.compareTo(java.math.BigDecimal.ZERO) < 0 || factor.compareTo(java.math.BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("El factor de penalització ha d'estar entre 0 i 1");
            }
            exam.setPenalitzacioChoice(factor);
        }
        if (req.unaPreguntaPerPantalla() != null) {
            exam.setUnaPreguntaPerPantalla(req.unaPreguntaPerPantalla());
        }
        if (req.title() != null) {
            String titol = req.title().strip();
            if (titol.isEmpty()) throw new IllegalArgumentException("El títol no pot estar buit");
            if (titol.length() > 255) throw new IllegalArgumentException("El títol pot tenir com a màxim 255 caràcters");
            exam.setTitle(titol);
        }
        if (req.durada() != null && req.durada() != exam.getDurada()) {
            // Amb l'examen actiu hi pot haver alumnes fent-lo: canviar-la mouria el seu rellotge
            if (exam.getStatus() != ExamStatus.DRAFT) {
                throw new IllegalStateException("Només es pot canviar la durada d'un examen en esborrany");
            }
            if (req.durada() < 1 || req.durada() > DURADA_MAXIMA) {
                throw new IllegalArgumentException("La durada ha de ser entre 1 i " + DURADA_MAXIMA + " minuts");
            }
            if (exam.getScheduledAt() != null && exam.getScheduledGrup() != null) {
                validateNoConflict(exam.getId(), exam.getScheduledGrup().getId(), exam.getScheduledAt(), req.durada());
            }
            exam.setDurada(req.durada());
        }
        return ExamDto.from(examRepository.save(exam), true);
    }

    public void validateNoConflict(UUID examId, UUID grupId, LocalDateTime newStart, int durada) {
        LocalDateTime newEnd = newStart.plusMinutes(durada);

        examRepository.findScheduledForGrup(grupId, examId).forEach(other -> {
            LocalDateTime oStart = other.getScheduledAt();
            LocalDateTime oEnd   = oStart.plusMinutes(other.getDurada());
            if (newStart.isBefore(oEnd) && newEnd.isAfter(oStart)) {
                throw new IllegalStateException(String.format(
                        "Conflicte: «%s» ja programat de %s a %s per a aquest grup",
                        other.getTitle(), com.examplatform.util.HoraLocal.format(oStart, "dd/MM HH:mm"),
                        com.examplatform.util.HoraLocal.format(oEnd, "dd/MM HH:mm")));
            }
        });

    }

    public void assertOwnership(Exam exam, User user) {
        if (!potGestionar(exam, user)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No tens permís per modificar aquest examen");
        }
    }

    /** Pot gestionar l'examen: admin, el creador o un professor que imparteix el mòdul. */
    public boolean potGestionar(Exam exam, User user) {
        if (user.getRole() == com.examplatform.domain.model.Role.ADMIN) return true;
        if (exam.getCreatedBy().getId().equals(user.getId())) return true;
        return exam.getModul() != null
                && imparticioRepository.professorImparteixModul(user.getId(), exam.getModul().getId());
    }

    @Transactional(readOnly = true)
    public Exam getEntity(UUID id) {
        return examRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Examen no trobat: " + id));
    }
}

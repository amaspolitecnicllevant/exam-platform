package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.domain.port.ScriptExecutor;
import com.examplatform.dto.AnswerDto;
import com.examplatform.dto.ExecutionResultDto;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.persistence.ExecutionRepository;
import com.examplatform.infrastructure.persistence.QuestionFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorrectionService {

    private static final Duration REGEX_TIMEOUT = Duration.ofSeconds(2);
    private static final String TIMEOUT_MARK = "[Timeout:";

    private final AnswerRepository answerRepository;
    private final ExecutionRepository executionRepository;
    private final ScriptExecutor executor;
    private final QuestionFileRepository questionFileRepository;
    private final ExamService examService;
    private final TransactionTemplate tx;

    /** Dades necessàries per executar una resposta, llegides dins d'una transacció curta. */
    private record Preparacio(QuestionType tipus, String code, String testScript,
                              List<Path> dataFiles, boolean senseResposta) {}

    /**
     * Execució demanada per un usuari: l'alumne només les seves respostes; el professor,
     * respostes d'exàmens que pot gestionar.
     *
     * <p>No és {@code @Transactional}: l'execució Docker (fins al timeout) es fa sense cap
     * connexió a la BD oberta; si no, poques execucions simultànies esgotarien el pool.
     */
    public ExecutionResultDto executeAs(UUID answerId, User user) {
        boolean alumne = user.getRole() == Role.STUDENT;
        Preparacio p = tx.execute(status -> {
            Answer answer = getAnswer(answerId);
            assertPotAccedir(answer, user);
            if (alumne && answer.getSession().getStatus() != SessionStatus.IN_PROGRESS) {
                throw new IllegalStateException("Només pots executar codi mentre fas l'examen");
            }
            if (alumne) SessionService.assertExamenActiu(answer.getSession().getExam());
            // L'alumne executa només el seu codi: sense el :::test del professor (el seu codi el
            // podria llegir) i sense nota. La correcció completa es fa en entregar.
            return prepara(answer, !alumne);
        });
        if (p.senseResposta()) return SENSE_RESPOSTA;

        ScriptExecutor.ExecutionResult result = executa(p);
        if (alumne) {
            tx.executeWithoutResult(status -> {
                Answer answer = getAnswer(answerId);
                answer.setExecutionOutput(result.output());
                answerRepository.save(answer);
            });
        } else {
            desaCorreccio(answerId, result);
        }
        return ExecutionResultDto.from(result);
    }

    /** Execució de correcció (amb :::test i proposta de nota). Sense transacció durant Docker. */
    public ExecutionResultDto execute(UUID answerId) {
        Preparacio p = tx.execute(status -> prepara(getAnswer(answerId), true));
        if (p.senseResposta()) return SENSE_RESPOSTA;
        ScriptExecutor.ExecutionResult result = executa(p);
        desaCorreccio(answerId, result);
        return ExecutionResultDto.from(result);
    }

    private static final ExecutionResultDto SENSE_RESPOSTA = new ExecutionResultDto("(sense resposta)", -1, 0, false);

    private Preparacio prepara(Answer answer, boolean ambTest) {
        Question question = answer.getQuestion();
        if (!question.getTipus().isExecutable()) {
            throw new IllegalArgumentException("Aquesta pregunta no és executable");
        }
        String code = answer.getContingut();
        if (code == null || code.isBlank()) {
            return new Preparacio(question.getTipus(), null, null, List.of(), true);
        }
        List<Path> dataFiles = questionFileRepository.findByQuestionId(question.getId())
                .stream().map(f -> Path.of(f.getStoredPath())).toList();
        String test = ambTest && question.getTestScript() != null && question.getTipus().isScript()
                ? question.getTestScript() : null;
        return new Preparacio(question.getTipus(), code, test, dataFiles, false);
    }

    private ScriptExecutor.ExecutionResult executa(Preparacio p) {
        return p.testScript() != null
                ? executor.executeWithTest(p.tipus(), p.code(), p.testScript(), p.dataFiles())
                : executor.execute(p.tipus(), p.code(), p.dataFiles());
    }

    private void desaCorreccio(UUID answerId, ScriptExecutor.ExecutionResult result) {
        tx.executeWithoutResult(status -> {
            Answer answer = getAnswer(answerId);
            executionRepository.save(Execution.builder()
                    .answer(answer)
                    .output(result.output())
                    .exitCode(result.exitCode())
                    .durationMs(result.durationMs())
                    .build());
            answer.setExecutionOutput(result.output());
            aplica(answer, autoCorrect(answer.getQuestion(), result));
            answerRepository.save(answer);
        });
    }

    /**
     * Proposa la nota de les respostes que no cal executar, en entregar la sessió:
     * preguntes de text amb {@code :::clau} i preguntes executables sense resposta.
     * Les respostes de codi s'executen després, en segon pla ({@link CorreccioEnSegonPla}).
     */
    @Transactional
    public void proposaSenseExecucio(ExamSession session) {
        for (Answer answer : answerRepository.findBySessionId(session.getId())) {
            Question q = answer.getQuestion();
            if (q.isAnulada()) continue;

            if (q.getTipus().isExecutable()) {
                if (answer.getContingut() == null || answer.getContingut().isBlank()) {
                    aplica(answer, Proposta.of(BigDecimal.ZERO, "Sense resposta"));
                    answerRepository.save(answer);
                }
            } else if (esText(q.getTipus()) && q.getClaus() != null) {
                try {
                    aplica(answer, ClausCorreccio.parse(q.getClaus(), q.getPunts())
                            .avalua(answer.getContingut()));
                    answerRepository.save(answer);
                } catch (IllegalArgumentException e) {
                    log.warn("Conceptes clau invàlids a la pregunta {}: {}", q.getId(), e.getMessage());
                }
            }
        }
    }

    @Transactional
    public AnswerDto setManualScore(UUID answerId, BigDecimal score, User professor) {
        Answer answer = getAnswer(answerId);
        examService.assertOwnership(answer.getSession().getExam(), professor);
        BigDecimal max = answer.getQuestion().getPunts();
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                    "La puntuació ha de ser entre 0 i " + max);
        }
        answer.setManualScore(score);
        answer.setCorrectedAt(LocalDateTime.now());
        return AnswerDto.fromProfessor(answerRepository.save(answer));
    }

    static final int MAX_COMENTARI = 2000;

    /** Comentari del professor a una resposta. Buit o null l'esborra. */
    @Transactional
    public AnswerDto setComentari(UUID answerId, String comentari, User professor) {
        Answer answer = getAnswer(answerId);
        examService.assertOwnership(answer.getSession().getExam(), professor);
        String net = comentari == null || comentari.isBlank() ? null : comentari.strip();
        if (net != null && net.length() > MAX_COMENTARI) {
            throw new IllegalArgumentException("El comentari no pot superar " + MAX_COMENTARI + " caràcters");
        }
        answer.setComentari(net);
        return AnswerDto.fromProfessor(answerRepository.save(answer));
    }

    /** El professor accepta la nota proposada d'una resposta: passa a ser la nota revisada. */
    @Transactional
    public AnswerDto acceptaProposta(UUID answerId, User professor) {
        Answer answer = getAnswer(answerId);
        examService.assertOwnership(answer.getSession().getExam(), professor);
        if (answer.getAutoScore() == null) {
            throw new IllegalStateException("Aquesta resposta no té nota proposada");
        }
        accepta(answer);
        return AnswerDto.fromProfessor(answerRepository.save(answer));
    }

    /**
     * Accepta totes les propostes pendents de revisar d'un examen (o només d'una sessió).
     *
     * @return nombre de respostes acceptades
     */
    @Transactional
    public int acceptaPropostes(UUID examId, UUID sessionId, User professor) {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, professor);
        List<Answer> pendents = answerRepository.findPendentsRevisio(examId, PENDENTS_TIPUS_EXCLOSOS);
        int acceptades = 0;
        for (Answer answer : pendents) {
            if (answer.getAutoScore() == null) continue;
            if (sessionId != null && !answer.getSession().getId().equals(sessionId)) continue;
            accepta(answer);
            answerRepository.save(answer);
            acceptades++;
        }
        return acceptades;
    }

    /** Tipus de pregunta que no necessiten revisió del professor (es corregeixen soles o no puntuen). */
    public static final List<QuestionType> PENDENTS_TIPUS_EXCLOSOS =
            List.of(QuestionType.CHOICE, QuestionType.SECTION);

    private void assertPotAccedir(Answer answer, User user) {
        if (user.getRole() == Role.STUDENT) {
            if (!answer.getSession().getStudent().getId().equals(user.getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "No pots executar respostes d'altres alumnes");
            }
        } else {
            examService.assertOwnership(answer.getSession().getExam(), user);
        }
    }

    private static void accepta(Answer answer) {
        answer.setManualScore(answer.getAutoScore().max(BigDecimal.ZERO)
                .min(answer.getQuestion().getPunts()));
        answer.setCorrectedAt(LocalDateTime.now());
    }

    private static void aplica(Answer answer, Proposta proposta) {
        answer.setAutoScore(proposta.score());
        answer.setAutoFeedback(proposta.feedback());
    }

    private static boolean esText(QuestionType tipus) {
        return tipus == QuestionType.TEXT || tipus == QuestionType.SHORT || tipus == QuestionType.LONG;
    }

    Proposta autoCorrect(Question q, ScriptExecutor.ExecutionResult result) {
        String output = result.output() == null ? "" : result.output().trim();
        BigDecimal punts = q.getPunts();

        // Script amb test: exit 0 = ple punts
        if (q.getTestScript() != null) {
            if (result.succeeded()) return Proposta.of(punts, "✓ Supera el test de comprovació");
            if (isTimeout(result)) return Proposta.of(BigDecimal.ZERO,
                    Proposta.penalitzacio(punts, "temps d'execució esgotat"));
            return Proposta.of(BigDecimal.ZERO, Proposta.penalitzacio(punts,
                    "no supera el test de comprovació (exit " + result.exitCode() + ")"));
        }

        // Amb criteri de sortida, el programa també ha d'acabar bé (ni error ni timeout)
        boolean hasOutputCriterion = q.getOutputExact() != null
                || hasContainsLines(q.getOutputContains())
                || q.getOutputRegex() != null;
        if (hasOutputCriterion && !result.succeeded()) {
            return Proposta.of(BigDecimal.ZERO, Proposta.penalitzacio(punts, errorExecucio(result)));
        }

        // output-exact
        if (q.getOutputExact() != null) {
            return output.equalsIgnoreCase(q.getOutputExact().trim())
                    ? Proposta.of(punts, "✓ La sortida coincideix amb l'esperada")
                    : Proposta.of(BigDecimal.ZERO,
                            Proposta.penalitzacio(punts, "la sortida no coincideix amb l'esperada"));
        }

        // output-contains: nota proporcional a les línies esperades que apareixen
        if (hasContainsLines(q.getOutputContains())) {
            List<String> esperades = q.getOutputContains().lines()
                    .map(String::trim).filter(l -> !l.isEmpty()).toList();
            BigDecimal perLinia = punts.divide(BigDecimal.valueOf(esperades.size()), 4, RoundingMode.HALF_UP);
            String outputLower = output.toLowerCase();
            List<String> motius = new ArrayList<>();
            int trobades = 0;
            for (String linia : esperades) {
                if (outputLower.contains(linia.toLowerCase())) {
                    trobades++;
                } else {
                    motius.add(Proposta.penalitzacio(perLinia, "a la sortida hi falta «" + linia + "»"));
                }
            }
            if (motius.isEmpty()) {
                return Proposta.of(punts, "✓ La sortida conté tot l'esperat");
            }
            BigDecimal score = trobades == 0 ? BigDecimal.ZERO
                    : perLinia.multiply(BigDecimal.valueOf(trobades)).setScale(2, RoundingMode.HALF_UP);
            return new Proposta(score, motius);
        }

        // output-regex (amb límit de temps per evitar ReDoS)
        if (q.getOutputRegex() != null) {
            return regexMatches(q.getOutputRegex(), output)
                    ? Proposta.of(punts, "✓ La sortida compleix el patró esperat")
                    : Proposta.of(BigDecimal.ZERO,
                            Proposta.penalitzacio(punts, "la sortida no compleix el patró esperat"));
        }

        // Sense criteri: exit 0 = ple punts
        return result.succeeded()
                ? Proposta.of(punts, "✓ S'executa sense errors")
                : Proposta.of(BigDecimal.ZERO, Proposta.penalitzacio(punts, errorExecucio(result)));
    }

    private static boolean isTimeout(ScriptExecutor.ExecutionResult result) {
        return result.output() != null && result.output().contains(TIMEOUT_MARK);
    }

    private static String errorExecucio(ScriptExecutor.ExecutionResult result) {
        return isTimeout(result)
                ? "temps d'execució esgotat"
                : "l'execució acaba amb error (exit " + result.exitCode() + ")";
    }

    private static boolean hasContainsLines(String outputContains) {
        return outputContains != null && outputContains.lines().anyMatch(l -> !l.isBlank());
    }

    /**
     * Avalua la regex al mateix fil amb un termini: si el supera, la cerca s'avorta
     * (no queda consumint CPU en segon pla) i es considera que no hi ha coincidència.
     */
    private static boolean regexMatches(String regex, String output) {
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
            long deadline = System.nanoTime() + REGEX_TIMEOUT.toNanos();
            return pattern.matcher(new DeadlineCharSequence(output, deadline)).find();
        } catch (PatternSyntaxException | RegexTimeoutException e) {
            return false;
        }
    }

    private static final class RegexTimeoutException extends RuntimeException {
        RegexTimeoutException() {
            super(null, null, false, false);
        }
    }

    /** CharSequence que llança {@link RegexTimeoutException} quan s'hi accedeix passat el termini. */
    private record DeadlineCharSequence(CharSequence inner, long deadlineNanos) implements CharSequence {
        @Override
        public char charAt(int index) {
            if (System.nanoTime() > deadlineNanos) {
                throw new RegexTimeoutException();
            }
            return inner.charAt(index);
        }

        @Override
        public int length() {
            return inner.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineCharSequence(inner.subSequence(start, end), deadlineNanos);
        }

        @Override
        public String toString() {
            return inner.toString();
        }
    }

    private Answer getAnswer(UUID id) {
        return answerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Resposta no trobada: " + id));
    }
}

package com.examplatform.domain.service;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionFile;
import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.model.User;
import com.examplatform.domain.port.ExamParser;
import com.examplatform.dto.QuestionEditRequest;
import com.examplatform.dto.QuestionFileDto;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import com.examplatform.infrastructure.persistence.QuestionFileRepository;
import com.examplatform.infrastructure.persistence.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Edició del contingut d'un examen des de la web: reescriure, afegir, eliminar i reordenar preguntes,
 * i pujar-hi imatges. Només és possible mentre l'examen no té cap sessió d'alumne, perquè no canviï el
 * que han vist o respost. Cada pregunta es construeix passant-la pel mateix parser que la importació
 * ({@link ExamParser#parseQuestion}), de manera que les regles de validació són exactament les mateixes.
 */
@Service
@RequiredArgsConstructor
public class EditorPreguntesService {

    private static final Logger log = LoggerFactory.getLogger(EditorPreguntesService.class);

    /** Ordre provisional mentre es renumera, per no xocar amb la restricció única (examen, ordre). */
    private static final int ORDRE_PROVISIONAL = 10_000;
    private static final BigDecimal PUNTS_MAXIMS = new BigDecimal("10");
    private static final Map<String, String> TIPUS_IMATGE =
            Map.of("png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
                    "gif", "image/gif", "webp", "image/webp");

    private final ExamService examService;
    private final ExamParser parser;
    private final QuestionRepository questionRepository;
    private final QuestionFileRepository fileRepository;
    private final ExamSessionRepository sessionRepository;

    @Value("${execution.files-host-path}")
    private String filesHostPath;

    /** Mida màxima d'una imatge d'enunciat (bytes). */
    @Value("${exam.imatge-max-bytes:5242880}")
    private long imatgeMaxBytes;

    public record Estat(boolean editable, String motiu) {}

    @Transactional(readOnly = true)
    public Estat estat(UUID examId, User user) {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, user);
        return sessionRepository.existsByExamId(examId)
                ? new Estat(false, "L'examen ja té sessions d'alumnes: el text ja no es pot modificar. "
                        + "Pots duplicar l'examen per fer-ne una versió nova.")
                : new Estat(true, null);
    }

    private Exam examEditable(UUID examId, User user) {
        Exam exam = examService.getEntity(examId);
        examService.assertOwnership(exam, user);
        if (sessionRepository.existsByExamId(examId)) {
            throw new IllegalStateException("L'examen ja té sessions d'alumnes i no es pot modificar. "
                    + "Duplica'l per fer-ne una versió nova");
        }
        return exam;
    }

    private static Question pregunta(Exam exam, UUID questionId) {
        return exam.getQuestions().stream().filter(q -> q.getId().equals(questionId)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Pregunta no trobada: " + questionId));
    }

    // ── Reescriure, afegir, eliminar, moure ──────────────────────────────────

    @Transactional
    public Question actualitza(UUID examId, UUID questionId, QuestionEditRequest req, User user) {
        Exam exam = examEditable(examId, user);
        Question q = pregunta(exam, questionId);
        Question nova = construeix(q.getOrdre(), req);
        copia(nova, q);
        return questionRepository.save(q);
    }

    @Transactional
    public Question afegeix(UUID examId, QuestionEditRequest req, User user) {
        Exam exam = examEditable(examId, user);
        int n = exam.getQuestions().size();
        int pos = req.posicio() == null ? n + 1 : Math.max(1, Math.min(req.posicio(), n + 1));
        Question nova = construeix(pos, req);
        nova.setExam(exam);
        nova.setOrdre(ORDRE_PROVISIONAL * 10);
        Question desada = questionRepository.saveAndFlush(nova);

        List<Question> ordenades = new ArrayList<>(exam.getQuestions());
        ordenades.remove(desada);
        ordenades.add(pos - 1, desada);
        if (!exam.getQuestions().contains(desada)) exam.getQuestions().add(desada);
        renumera(ordenades);
        return desada;
    }

    @Transactional
    public void elimina(UUID examId, UUID questionId, User user) {
        Exam exam = examEditable(examId, user);
        Question q = pregunta(exam, questionId);
        List<QuestionFile> fitxers = fileRepository.findByQuestionId(questionId);
        List<Path> rutes = fitxers.stream().map(f -> Path.of(f.getStoredPath())).toList();
        fileRepository.deleteAll(fitxers);   // abans que la pregunta: si no, Hibernate els veu apuntant a una pregunta esborrada
        exam.getQuestions().remove(q);       // orphanRemoval l'esborra
        questionRepository.flush();
        renumera(new ArrayList<>(exam.getQuestions()));
        rutes.forEach(this::esborraDeDisc);
    }

    @Transactional
    public void mou(UUID examId, UUID questionId, int posicio, User user) {
        Exam exam = examEditable(examId, user);
        Question q = pregunta(exam, questionId);
        List<Question> ordenades = new ArrayList<>(exam.getQuestions());
        ordenades.remove(q);
        ordenades.add(Math.max(0, Math.min(posicio - 1, ordenades.size())), q);
        renumera(ordenades);
    }

    private void renumera(List<Question> ordenades) {
        for (int i = 0; i < ordenades.size(); i++) ordenades.get(i).setOrdre(ORDRE_PROVISIONAL + i);
        questionRepository.saveAllAndFlush(ordenades);
        for (int i = 0; i < ordenades.size(); i++) ordenades.get(i).setOrdre(i + 1);
        questionRepository.saveAllAndFlush(ordenades);
    }

    // ── Imatges ───────────────────────────────────────────────────────────────

    @Transactional
    public QuestionFileDto pujaImatge(UUID questionId, MultipartFile upload, User user) throws IOException {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("Pregunta no trobada: " + questionId));
        examEditable(q.getExam().getId(), user);

        String original = upload.getOriginalFilename() == null ? "" : upload.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        String contentType = TIPUS_IMATGE.get(ext);
        if (contentType == null) {
            throw new IllegalArgumentException("Format d'imatge no admès «" + ext + "». Usa PNG, JPG, GIF o WebP");
        }
        if (upload.isEmpty() || upload.getSize() > imatgeMaxBytes) {
            throw new IllegalArgumentException("La imatge ha de pesar com a màxim "
                    + (imatgeMaxBytes / 1024 / 1024) + " MB");
        }
        byte[] dades = upload.getBytes();
        if (!ambFirmaDImatge(dades, ext)) {
            throw new IllegalArgumentException("El fitxer no és una imatge " + ext.toUpperCase(Locale.ROOT) + " vàlida");
        }

        Path dir = Path.of(filesHostPath, "questions", questionId.toString());
        Files.createDirectories(dir);
        Path dest = dir.resolve("img-" + UUID.randomUUID().toString().substring(0, 8) + "." + ext);
        Files.write(dest, dades);

        String nom = Path.of(original.isBlank() ? "imatge." + ext : original).getFileName().toString()
                .replaceAll("[^a-zA-Z0-9._\\-]", "_");
        QuestionFile qf = fileRepository.save(QuestionFile.builder()
                .question(q).filename(nom).storedPath(dest.toString())
                .contentType(contentType).fileSize(dades.length).build());
        return QuestionFileDto.from(qf);
    }

    /** Comprova la signatura del fitxer: l'extensió sola no demostra que sigui una imatge. */
    static boolean ambFirmaDImatge(byte[] b, String ext) {
        if (b.length < 12) return false;
        return switch (ext) {
            case "png" -> (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "jpg", "jpeg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "gif" -> b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
            case "webp" -> b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }

    private void esborraDeDisc(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("No s'ha pogut esborrar {}", p, e);
        }
    }

    // ── Construcció i validació (via el parser de Markdown) ──────────────────

    private Question construeix(int ordre, QuestionEditRequest req) {
        try {
            return parser.parseQuestion(aMarkdown(ordre, req));
        } catch (IllegalArgumentException e) {
            // Els números de línia són del text intern generat, no de l'usuari: no els mostrem
            String msg = e.getMessage() == null ? "Pregunta no vàlida" : e.getMessage()
                    .replaceFirst("^Línia \\d+: ", "")
                    .replaceAll("\\s*\\(línia \\d+\\)", "");
            throw new IllegalArgumentException(msg, e);
        }
    }

    private static final Pattern ETIQUETA_PROHIBIDA = Pattern.compile("[\\[\\]\\n\\r]");

    /** Converteix el formulari a la sintaxi Markdown de la importació. */
    static String aMarkdown(int ordre, QuestionEditRequest r) {
        if (r.tipus() == null) throw new IllegalArgumentException("Cal indicar el tipus de pregunta");
        String enunciat = r.enunciat() == null ? "" : r.enunciat().strip();
        if (enunciat.isEmpty()) throw new IllegalArgumentException("L'enunciat no pot estar buit");

        if (r.tipus() == QuestionType.SECTION) {
            if (enunciat.contains("\n")) throw new IllegalArgumentException("El títol d'una secció ha de ser d'una sola línia");
            return "### " + enunciat;
        }

        BigDecimal punts = r.punts();
        if (punts == null || punts.signum() <= 0 || punts.compareTo(PUNTS_MAXIMS) > 0 || punts.scale() > 2) {
            throw new IllegalArgumentException("Els punts han de ser un nombre entre 0 i 10, amb com a màxim dos decimals");
        }
        StringBuilder md = new StringBuilder("## ").append(ordre).append(". [").append(r.tipus().name())
                .append("] [pts:").append(punts.stripTrailingZeros().toPlainString()).append(']');
        etiqueta(md, "ra", r.ra());
        etiqueta(md, "dif", r.dificultat());
        if (r.tipus() == QuestionType.CHOICE && Boolean.FALSE.equals(r.barrejarOpcions())) md.append(" [ordre:fix]");
        if (r.tipus() == QuestionType.FILE_UPLOAD && r.formatsPermesos() != null && !r.formatsPermesos().isEmpty()) {
            etiqueta(md, "formats", String.join(",", r.formatsPermesos()));
        }
        if (Boolean.TRUE.equals(r.ambApunts())) md.append(" [apunts]");
        md.append('\n').append(enunciat).append('\n');

        if (r.tipus() == QuestionType.CHOICE) {
            List<String> opcions = r.opcions() == null ? List.of() : r.opcions();
            for (int i = 0; i < opcions.size(); i++) {
                String text = opcions.get(i) == null ? "" : opcions.get(i).strip().replaceAll("\\s*\\R\\s*", " ");
                md.append("- ").append((char) ('a' + i)).append(") ").append(text).append('\n');
            }
            bloc(md, "model", r.correctChoice());
        } else {
            bloc(md, "model", r.modelResposta());
            bloc(md, "clau", r.claus());
            bloc(md, "output-contains", r.outputContains());
            bloc(md, "output-exact", r.outputExact());
            bloc(md, "output-regex", r.outputRegex());
            bloc(md, "test", r.testScript());
        }
        return md.toString();
    }

    private static void etiqueta(StringBuilder md, String nom, String valor) {
        if (valor == null || valor.isBlank()) return;
        if (ETIQUETA_PROHIBIDA.matcher(valor).find() || valor.length() > 100) {
            throw new IllegalArgumentException("El valor de «" + nom + "» no és vàlid (sense claudàtors ni salts de línia, màxim 100 caràcters)");
        }
        md.append(" [").append(nom).append(':').append(valor.strip()).append(']');
    }

    private static void bloc(StringBuilder md, String nom, String contingut) {
        if (contingut == null || contingut.isBlank()) return;
        md.append(":::").append(nom).append('\n').append(contingut.strip()).append("\n:::\n");
    }

    /** Copia el contingut d'una pregunta construïda sobre l'existent (conserva id, examen i ordre). */
    private static void copia(Question de, Question a) {
        a.setTipus(de.getTipus());
        a.setEnunciat(de.getEnunciat());
        a.setPunts(de.getPunts());
        a.setModelResposta(de.getModelResposta());
        a.setOutputContains(de.getOutputContains());
        a.setOutputExact(de.getOutputExact());
        a.setOutputRegex(de.getOutputRegex());
        a.setTestScript(de.getTestScript());
        a.setClaus(de.getClaus());
        a.setChoices(de.getChoices());
        a.setCorrectChoice(de.getCorrectChoice());
        a.setBarrejarOpcions(de.isBarrejarOpcions());
        a.setAmbApunts(de.isAmbApunts());
        a.setRa(de.getRa());
        a.setDificultat(de.getDificultat());
        a.setFormatsPermesos(de.getFormatsPermesos());
    }
}

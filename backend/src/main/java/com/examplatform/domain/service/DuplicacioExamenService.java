package com.examplatform.domain.service;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.ExamStatus;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionFile;
import com.examplatform.domain.model.User;
import com.examplatform.dto.ExamDto;
import com.examplatform.infrastructure.persistence.ExamRepository;
import com.examplatform.infrastructure.persistence.QuestionFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Duplica un examen com a esborrany nou: mateixes preguntes (amb les modificacions fetes des de la web),
 * configuració i fitxers de dades. No es copien la programació, les sessions ni l'estat de correcció
 * (notes visibles, preguntes anul·lades com a bonus).
 */
@Service
@RequiredArgsConstructor
public class DuplicacioExamenService {

    private final ExamService examService;
    private final ExamRepository examRepository;
    private final QuestionFileRepository questionFileRepository;

    @Value("${execution.files-host-path}")
    private String filesHostPath;

    @Transactional
    public ExamDto duplica(UUID examId, User user) {
        Exam origen = examService.getEntity(examId);
        examService.assertOwnership(origen, user);

        Exam copia = Exam.builder()
                .title(titolCopia(origen.getTitle()))
                .durada(origen.getDurada())
                .instruccions(origen.getInstruccions())
                .status(ExamStatus.DRAFT)
                .createdBy(user)
                .rawMd(origen.getRawMd())
                .modul(origen.getModul())
                .aula(origen.getAula())
                .penalitzacioChoice(origen.getPenalitzacioChoice())
                .unaPreguntaPerPantalla(origen.isUnaPreguntaPerPantalla())
                .build();
        for (Question q : origen.getQuestions()) {
            copia.getQuestions().add(Question.builder()
                    .exam(copia)
                    .ordre(q.getOrdre())
                    .tipus(q.getTipus())
                    .enunciat(q.getEnunciat())
                    .punts(q.getPunts())
                    .modelResposta(q.getModelResposta())
                    .outputContains(q.getOutputContains())
                    .outputExact(q.getOutputExact())
                    .outputRegex(q.getOutputRegex())
                    .testScript(q.getTestScript())
                    .claus(q.getClaus())
                    .choices(q.getChoices())
                    .correctChoice(q.getCorrectChoice())
                    .barrejarOpcions(q.isBarrejarOpcions())
                    .ambApunts(q.isAmbApunts())
                    .ra(q.getRa())
                    .dificultat(q.getDificultat())
                    .formatsPermesos(q.getFormatsPermesos())
                    .build());
        }
        Exam desat = examRepository.save(copia);

        // Fitxers de dades: cada pregunta nova té la seva carpeta (el sandbox munta la de la pregunta)
        List<Path> copiats = new ArrayList<>();
        Map<UUID, UUID> idsCopiats = new HashMap<>();
        try {
            for (int i = 0; i < origen.getQuestions().size(); i++) {
                copiaFitxers(origen.getQuestions().get(i), desat.getQuestions().get(i), copiats, idsCopiats);
            }
            // Les imatges de l'enunciat (![](fitxer:id)) han d'apuntar als fitxers copiats, no als de l'original
            for (Question q : desat.getQuestions()) {
                String text = ReferenciesImatge.remapeja(q.getEnunciat(), idsCopiats);
                if (!text.equals(q.getEnunciat())) q.setEnunciat(text);
            }
        } catch (RuntimeException e) {
            // La transacció es desfà: esborrem els fitxers ja copiats perquè no quedin orfes
            copiats.forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            throw e;
        }
        return ExamDto.from(desat, true);
    }

    private void copiaFitxers(Question origen, Question desti, List<Path> copiats, Map<UUID, UUID> idsCopiats) {
        List<QuestionFile> fitxers = questionFileRepository.findByQuestionId(origen.getId());
        if (fitxers.isEmpty()) return;
        Path dir = Path.of(filesHostPath, "questions", desti.getId().toString());
        try {
            Files.createDirectories(dir);
            for (QuestionFile f : fitxers) {
                Path font = Path.of(f.getStoredPath());
                if (!Files.isRegularFile(font)) {
                    throw new IllegalStateException(
                            "No es pot duplicar: falta el fitxer «" + f.getFilename() + "» de la pregunta " + origen.getOrdre());
                }
                Path nou = dir.resolve(font.getFileName().toString());
                Files.copy(font, nou);
                copiats.add(nou);
                QuestionFile desat = questionFileRepository.save(QuestionFile.builder()
                        .question(desti)
                        .filename(f.getFilename())
                        .storedPath(nou.toString())
                        .contentType(f.getContentType())
                        .fileSize(f.getFileSize())
                        .build());
                idsCopiats.put(f.getId(), desat.getId());
            }
        } catch (IOException e) {
            throw new IllegalStateException("No s'han pogut copiar els fitxers de dades de la pregunta " + origen.getOrdre(), e);
        }
    }

    static String titolCopia(String titol) {
        String t = "Còpia de " + titol;
        return t.length() > 255 ? t.substring(0, 255) : t;
    }
}

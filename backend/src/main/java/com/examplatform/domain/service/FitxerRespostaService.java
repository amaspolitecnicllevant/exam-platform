package com.examplatform.domain.service;

import com.examplatform.domain.model.Answer;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.model.Role;
import com.examplatform.dto.AnswerDto;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import com.examplatform.infrastructure.storage.FitxersRespostaStorage;
import com.examplatform.domain.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Lliurament de fitxers (Word, Excel, Packet Tracer…) a les preguntes {@code FILE_UPLOAD}. L'alumne
 * només hi pot pujar mentre la sessió és activa, amb les mateixes comprovacions que en desar una
 * resposta (examen actiu, aula, temps). Un fitxer per pregunta: pujar-ne un de nou substitueix l'anterior.
 */
@Service
public class FitxerRespostaService {

    /** Fitxer a descarregar amb les dades per a la resposta HTTP. */
    public record Descarrega(Resource recurs, String nom) {}

    private final SessionService sessionService;
    private final AnswerRepository answerRepository;
    private final ConfiguracioService configuracioService;
    private final FitxersRespostaStorage storage;
    private final ExamService examService;
    private final long midaMaxima;

    public FitxerRespostaService(SessionService sessionService,
                                 AnswerRepository answerRepository,
                                 ConfiguracioService configuracioService,
                                 FitxersRespostaStorage storage,
                                 ExamService examService,
                                 @Value("${exam.upload.max-bytes:10485760}") long midaMaxima) {
        this.sessionService = sessionService;
        this.answerRepository = answerRepository;
        this.configuracioService = configuracioService;
        this.storage = storage;
        this.examService = examService;
        this.midaMaxima = midaMaxima;
    }

    @Transactional
    public AnswerDto puja(UUID sessionId, UUID questionId, MultipartFile fitxer, User alumne, String clientIp)
            throws IOException {
        assertPujadaActiva();
        Answer answer = sessionService.respostaPerEscriure(sessionId, questionId, alumne, clientIp);
        Question pregunta = answer.getQuestion();
        assertPreguntaDeFitxer(pregunta);

        if (fitxer == null || fitxer.isEmpty()) {
            throw new IllegalArgumentException("El fitxer és buit");
        }
        String nomOriginal = nomNet(fitxer.getOriginalFilename());
        String ext = FormatsFitxer.extensio(nomOriginal);
        List<String> admesos = FormatsFitxer.deLaPregunta(pregunta.getFormatsPermesos());
        if (ext.isEmpty() || !admesos.contains(ext)) {
            throw new IllegalArgumentException("Aquesta pregunta només admet fitxers "
                    + String.join(", ", admesos.stream().map(e -> "." + e).toList()));
        }
        if (fitxer.getSize() > midaMaxima) {
            throw new IllegalArgumentException("El fitxer supera la mida màxima de " + (midaMaxima / (1024 * 1024)) + " MB");
        }

        FitxersRespostaStorage.Desat desat;
        try {
            desat = storage.desa(sessionId, questionId, ext, fitxer.getInputStream(), midaMaxima);
        } catch (FitxersRespostaStorage.MassaGran e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        if (!FormatsFitxer.signaturaValida(ext, desat.capcalera(), desat.capcaleraLlargada())) {
            storage.esborra(desat.ruta().toString());
            throw new IllegalArgumentException("El contingut del fitxer no correspon a un fitxer ." + ext
                    + " (potser l'has canviat de nom o està malmès)");
        }

        String rutaAnterior = answer.getFitxerRuta();
        answer.setFitxerNom(nomOriginal);
        answer.setFitxerRuta(desat.ruta().toString());
        answer.setFitxerMida(desat.mida());
        answer.setFitxerSha256(desat.sha256());
        answer.setFitxerPujatEl(LocalDateTime.now());
        // Així la pregunta compta com a contestada a tot arreu (monitor, pendents de nota…)
        answer.setContingut(nomOriginal);
        Answer desada = answerRepository.save(answer);
        if (rutaAnterior != null) storage.esborra(rutaAnterior);
        return AnswerDto.senseNotes(desada);
    }

    @Transactional
    public AnswerDto esborra(UUID sessionId, UUID questionId, User alumne, String clientIp) {
        Answer answer = sessionService.respostaPerEscriure(sessionId, questionId, alumne, clientIp);
        assertPreguntaDeFitxer(answer.getQuestion());
        if (!answer.teFitxer()) {
            throw new NoSuchElementException("No hi ha cap fitxer pujat en aquesta pregunta");
        }
        storage.esborra(answer.getFitxerRuta());
        answer.setFitxerNom(null);
        answer.setFitxerRuta(null);
        answer.setFitxerMida(null);
        answer.setFitxerSha256(null);
        answer.setFitxerPujatEl(null);
        answer.setContingut(null);
        return AnswerDto.senseNotes(answerRepository.save(answer));
    }

    /** Descàrrega: l'alumne propietari de la sessió, o qui gestiona l'examen. */
    @Transactional(readOnly = true)
    public Descarrega descarrega(UUID sessionId, UUID questionId, User usuari) {
        Answer answer = answerRepository.findBySessionIdAndQuestionId(sessionId, questionId)
                .orElseThrow(() -> new NoSuchElementException("No hi ha cap fitxer pujat en aquesta pregunta"));
        if (usuari.getRole() == Role.STUDENT) {
            if (!answer.getSession().getStudent().getId().equals(usuari.getId())) {
                throw new AccessDeniedException("No tens accés als fitxers d'aquesta sessió");
            }
        } else if (!examService.potGestionar(answer.getSession().getExam(), usuari)) {
            throw new AccessDeniedException("No tens accés als fitxers d'aquest examen");
        }
        if (!answer.teFitxer()) {
            throw new NoSuchElementException("No hi ha cap fitxer pujat en aquesta pregunta");
        }
        Path ruta = Path.of(answer.getFitxerRuta());
        if (!Files.isReadable(ruta)) {
            throw new IllegalStateException("El fitxer ja no està disponible: " + answer.getFitxerNom());
        }
        return new Descarrega(new PathResource(ruta), answer.getFitxerNom());
    }

    private void assertPujadaActiva() {
        if (Boolean.FALSE.equals(configuracioService.get().getPujadaFitxersActiva())) {
            throw new IllegalStateException("La pujada de fitxers està desactivada. Consulta el professor.");
        }
    }

    private static void assertPreguntaDeFitxer(Question q) {
        if (q.getTipus() != QuestionType.FILE_UPLOAD) {
            throw new IllegalArgumentException("Aquesta pregunta no és de lliurament de fitxer");
        }
    }

    /** Nom sense directoris ni caràcters estranys (només es mostra; mai forma part de la ruta del disc). */
    static String nomNet(String brut) {
        if (brut == null || brut.isBlank()) return "fitxer";
        String nom = brut.replace('\\', '/');
        nom = nom.substring(nom.lastIndexOf('/') + 1);
        nom = nom.replaceAll("[\\p{Cntrl}]", "").strip();
        if (nom.length() > 200) {
            String ext = FormatsFitxer.extensio(nom);
            nom = nom.substring(0, 190) + (ext.isEmpty() ? "" : "." + ext);
        }
        return nom.isBlank() ? "fitxer" : nom;
    }
}

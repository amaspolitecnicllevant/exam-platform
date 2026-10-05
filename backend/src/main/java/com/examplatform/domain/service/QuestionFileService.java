package com.examplatform.domain.service;

import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionFile;
import com.examplatform.dto.QuestionFileDto;
import com.examplatform.infrastructure.persistence.QuestionFileRepository;
import com.examplatform.infrastructure.persistence.QuestionRepository;
import com.examplatform.infrastructure.persistence.ExamSessionRepository;
import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionFileService {

    private final QuestionFileRepository fileRepository;
    private final QuestionRepository questionRepository;
    private final ExamService examService;
    private final ExamSessionRepository sessionRepository;

    /** Fitxer a descarregar amb les dades per a la resposta HTTP. */
    public record Descarrega(Resource resource, String contentType, String filename) {}

    private Question pregunta(UUID questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("Pregunta no trobada: " + questionId));
    }

    /** Veure els fitxers: qui gestiona l'examen, o un alumne que en té una sessió. */
    private void assertPotVeure(Question q, User user) {
        if (user.getRole() == Role.STUDENT) {
            if (sessionRepository.findByExamIdAndStudentId(q.getExam().getId(), user.getId()).isEmpty()) {
                throw new AccessDeniedException("No tens accés als fitxers d'aquest examen");
            }
        } else if (!examService.potGestionar(q.getExam(), user)) {
            throw new AccessDeniedException("No tens accés als fitxers d'aquest examen");
        }
    }

    @Value("${execution.files-host-path}")
    private String filesHostPath;

    @Transactional(readOnly = true)
    public List<QuestionFileDto> list(UUID questionId, User user) {
        assertPotVeure(pregunta(questionId), user);
        return fileRepository.findByQuestionId(questionId).stream()
                .map(QuestionFileDto::from).toList();
    }

    @Transactional
    public QuestionFileDto upload(UUID questionId, MultipartFile upload, User user) throws IOException {
        Question question = pregunta(questionId);
        examService.assertOwnership(question.getExam(), user);

        String sanitized = sanitizeFilename(upload.getOriginalFilename());
        Path dir = Path.of(filesHostPath, "questions", questionId.toString());
        Files.createDirectories(dir);

        // Si ja existeix un fitxer amb el mateix nom, afegim un sufix UUID
        Path dest = dir.resolve(sanitized);
        if (Files.exists(dest)) {
            String stem  = sanitized.contains(".") ? sanitized.substring(0, sanitized.lastIndexOf('.')) : sanitized;
            String ext   = sanitized.contains(".") ? sanitized.substring(sanitized.lastIndexOf('.'))    : "";
            dest = dir.resolve(stem + "-" + UUID.randomUUID().toString().substring(0, 8) + ext);
        }

        upload.transferTo(dest);

        QuestionFile qf = QuestionFile.builder()
                .question(question)
                .filename(sanitized)
                .storedPath(dest.toString())
                .contentType(upload.getContentType())
                .fileSize(upload.getSize())
                .build();

        return QuestionFileDto.from(fileRepository.save(qf));
    }

    @Transactional(readOnly = true)
    public Descarrega download(UUID fileId, User user) {
        QuestionFile qf = fileRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("Fitxer no trobat: " + fileId));
        assertPotVeure(qf.getQuestion(), user);
        try {
            Path path = Path.of(qf.getStoredPath());
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalStateException("Fitxer no accessible: " + qf.getFilename());
            }
            String contentType = qf.getContentType() != null ? qf.getContentType() : "application/octet-stream";
            return new Descarrega(resource, contentType, qf.getFilename());
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Ruta invàlida: " + qf.getFilename(), e);
        }
    }

    @Transactional
    public void delete(UUID questionId, UUID fileId, User user) {
        examService.assertOwnership(pregunta(questionId).getExam(), user);
        QuestionFile qf = fileRepository.findByIdAndQuestionId(fileId, questionId)
                .orElseThrow(() -> new NoSuchElementException("Fitxer no trobat"));
        try { Files.deleteIfExists(Path.of(qf.getStoredPath())); } catch (IOException ignored) {}
        fileRepository.delete(qf);
    }

    public Path questionFilesDir(UUID questionId) {
        return Path.of(filesHostPath, "questions", questionId.toString());
    }

    private String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) return "file";
        String name = Path.of(raw).getFileName().toString();
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}

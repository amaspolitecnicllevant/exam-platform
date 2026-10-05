package com.examplatform.controller;

import com.examplatform.domain.model.User;
import com.examplatform.domain.service.QuestionFileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.examplatform.dto.QuestionFileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class QuestionFileController {

    private final QuestionFileService fileService;
    private final com.examplatform.domain.service.AuditLogService auditLog;

    @GetMapping("/api/questions/{questionId}/files")
    @PreAuthorize("isAuthenticated()")
    public List<QuestionFileDto> list(@PathVariable UUID questionId,
                                      @AuthenticationPrincipal User user) {
        return fileService.list(questionId, user);
    }

    @PostMapping(value = "/api/questions/{questionId}/files", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ResponseEntity<QuestionFileDto> upload(@PathVariable UUID questionId,
                                                  @RequestParam("file") MultipartFile file,
                                                  @AuthenticationPrincipal User user) throws IOException {
        QuestionFileDto result = fileService.upload(questionId, file, user);
        auditLog.log(user.getId(), "FILE_UPLOADED", "pregunta " + questionId + ": " + result.filename());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/api/questions/{questionId}/files/{fileId}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID questionId,
                                       @PathVariable UUID fileId,
                                       @AuthenticationPrincipal User user) {
        fileService.delete(questionId, fileId, user);
        auditLog.log(user.getId(), "FILE_DELETED", "pregunta " + questionId + " fitxer " + fileId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/files/{fileId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> download(@PathVariable UUID fileId,
                                             @AuthenticationPrincipal User user) {
        QuestionFileService.Descarrega d = fileService.download(fileId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.filename() + "\"")
                .body(d.resource());
    }
}

package com.examplatform.controller;

import com.examplatform.domain.model.User;
import com.examplatform.domain.service.GrupService;
import com.examplatform.dto.GrupDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/grups")
@RequiredArgsConstructor
public class GrupController {

    private final GrupService grupService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public List<GrupDto> list(@AuthenticationPrincipal User user) {
        return grupService.findAll(user);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public GrupDto create(@Valid @RequestBody GrupDto.CreateRequest req,
                          @AuthenticationPrincipal User user) {
        return grupService.create(req.name(), user);
    }

    @PutMapping("/{grupId}/students")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public GrupDto setStudents(@PathVariable UUID grupId,
                               @RequestBody GrupDto.AddStudentsRequest req,
                               @AuthenticationPrincipal User user) {
        return grupService.setStudents(grupId, req.studentIds(), user);
    }

    @PostMapping("/{grupId}/assignar-examen/{examId}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public ResponseEntity<Map<String, Object>> assignExam(@PathVariable UUID grupId,
                                                          @PathVariable UUID examId,
                                                          @AuthenticationPrincipal User user) {
        int created = grupService.assignExam(grupId, examId, user);
        return ResponseEntity.ok(Map.of(
                "sessionsCreades", created,
                "missatge", created + " sessions creades"
        ));
    }

    @PatchMapping("/{grupId}/modul/{modulId}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    public GrupDto assignModul(@PathVariable UUID grupId,
                               @PathVariable UUID modulId,
                               @AuthenticationPrincipal User user) {
        return grupService.assignModul(grupId, modulId, user);
    }

    @DeleteMapping("/{grupId}")
    @PreAuthorize("hasAnyRole('PROFESSOR','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID grupId,
                       @AuthenticationPrincipal User user) {
        grupService.delete(grupId, user);
    }
}

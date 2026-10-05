package com.examplatform.controller;

import com.examplatform.domain.model.Role;
import com.examplatform.domain.service.AuditLogService;
import com.examplatform.domain.service.UserService;
import com.examplatform.dto.UserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final com.examplatform.domain.service.ImportacioUsuarisService importacioService;
    private final AuditLogService auditLog;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSOR')")
    public List<UserDto> findAll() {
        return userService.findAll();
    }

    @GetMapping("/role/{role}")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSOR')")
    public List<UserDto> findByRole(@PathVariable Role role) {
        return userService.findByRole(role);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSOR')")
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserDto.CreateRequest req,
                                          @AuthenticationPrincipal com.examplatform.domain.model.User caller) {
        UserDto result = userService.create(req, caller);
        auditLog.log(caller.getId(), "USER_CREATED", req.email());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal com.examplatform.domain.model.User caller) {
        userService.delete(id, caller);
        auditLog.log(caller.getId(), "USER_DELETED", id.toString());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id,
                                              @RequestBody Map<String, String> body,
                                              @AuthenticationPrincipal com.examplatform.domain.model.User caller) {
        String pwd = body.get("password");
        if (pwd == null || pwd.length() < 8 || pwd.length() > 72)
            throw new IllegalArgumentException("La contrasenya ha de tenir entre 8 i 72 caràcters");
        userService.resetPassword(id, pwd);
        auditLog.log(caller.getId(), "PASSWORD_RESET", id.toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSOR')")
    public com.examplatform.dto.ImportacioDto importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "role", required = false) Role role,
            @AuthenticationPrincipal com.examplatform.domain.model.User caller) throws IOException {
        var r = importacioService.importa(file.getBytes(), role, caller);
        // Sense les contrasenyes generades: només les veu qui importa, una sola vegada
        auditLog.log(caller.getId(), "USERS_IMPORTED", "creats=" + r.created() + " existents=" + r.skipped()
                + " matriculats=" + r.matriculats() + " a grups=" + r.afegitsAGrup()
                + " grups nous=" + r.grupsCreats().size() + " errors=" + r.errors().size());
        return r;
    }
}

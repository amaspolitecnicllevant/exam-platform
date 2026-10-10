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
    private final com.examplatform.config.LoginRateLimiter rateLimiter;
    private final com.examplatform.config.JwtUtil jwtUtil;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSOR')")
    public List<UserDto> findAll(@AuthenticationPrincipal com.examplatform.domain.model.User caller) {
        return userService.findAll(caller);
    }

    @GetMapping("/role/{role}")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSOR')")
    public List<UserDto> findByRole(@PathVariable Role role,
                                    @AuthenticationPrincipal com.examplatform.domain.model.User caller) {
        return userService.findByRole(role, caller);
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

    /** Qualsevol usuari autenticat canvia la seva pròpia contrasenya (cal l'actual). */
    @PostMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.examplatform.dto.LoginResponse> canviaLaMevaContrasenya(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal com.examplatform.domain.model.User caller,
            jakarta.servlet.http.HttpServletRequest request) {
        String ip = com.examplatform.util.IpUtil.clientIp(request);
        rateLimiter.assertPermes(ip, caller.getEmail());
        com.examplatform.domain.model.User actualitzat;
        try {
            actualitzat = userService.canviaContrasenya(caller.getId(), body.get("actual"), body.get("nova"));
        } catch (IllegalArgumentException e) {
            // Només l'error de l'actual compta com a intent fallit: evita provar contrasenyes amb un token robat
            if (e.getMessage().contains("actual no és correcta")) rateLimiter.registraFallada(ip, caller.getEmail());
            throw e;
        }
        auditLog.log(caller.getId(), "PASSWORD_CHANGED", caller.getId().toString());
        // Les sessions anteriors queden tancades; aquesta continua amb un token nou
        return ResponseEntity.ok(new com.examplatform.dto.LoginResponse(jwtUtil.generateToken(actualitzat),
                actualitzat.getId(), actualitzat.getName(), actualitzat.getEmail(), actualitzat.getRole()));
    }

    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id,
                                              @RequestBody Map<String, String> body,
                                              @AuthenticationPrincipal com.examplatform.domain.model.User caller) {
        userService.resetPassword(id, body.get("password"));
        auditLog.log(caller.getId(), "PASSWORD_RESET", id.toString());
        return ResponseEntity.noContent().build();
    }

    /** Tanca totes les sessions obertes d'un usuari (per exemple, si se li ha robat un token o ha deixat una sessió oberta). */
    @PostMapping("/{id}/revoca-sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revocaSessions(@PathVariable UUID id,
                                               @AuthenticationPrincipal com.examplatform.domain.model.User caller) {
        userService.revocaSessions(id);
        auditLog.log(caller.getId(), "SESSIONS_REVOCADES", id.toString());
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

package com.examplatform.controller;

import com.examplatform.config.JwtUtil;
import com.examplatform.domain.model.User;
import com.examplatform.dto.LoginRequest;
import com.examplatform.dto.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;
    private final com.examplatform.config.LoginRateLimiter rateLimiter;
    private final com.examplatform.domain.service.AuditLogService auditLog;

    /** Un bloqueig es registra un cop per compte i adreça (si no, un atac ompliria l'auditoria). */
    private final com.examplatform.util.LimitadorDAvisos bloquejosAvisats =
            new com.examplatform.util.LimitadorDAvisos(java.time.Duration.ofMinutes(15));

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               jakarta.servlet.http.HttpServletRequest request) {
        String ip = com.examplatform.util.IpUtil.clientIp(request);
        String compte = com.examplatform.util.LimitadorDAvisos.net(req.email(), 100).toLowerCase(java.util.Locale.ROOT);
        try {
            rateLimiter.assertPermes(ip, req.email());
        } catch (org.springframework.web.server.ResponseStatusException e) {
            if (bloquejosAvisats.permet(compte + "|" + ip)) auditLog.log(null, "LOGIN_BLOQUEJAT", compte);
            throw e;
        }
        Authentication auth;
        try {
            auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            rateLimiter.registraFallada(ip, req.email());
            auditLog.log(null, "LOGIN_FALLIT", compte);
            throw e;
        }

        User user = (User) auth.getPrincipal();
        registraEntradaDelPersonal(user, ip);
        String token = jwtUtil.generateToken(user);
        return ResponseEntity.ok(
                new LoginResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole()));
    }

    /**
     * Les entrades del professorat i l'administració es registren (les dels alumnes no: serien milers). Si un administrador entra
     * des d'una adreça des de la qual mai no havia entrat, es deixa constància perquè es pugui avisar.
     */
    private void registraEntradaDelPersonal(User user, String ip) {
        if (user.getRole() == com.examplatform.domain.model.Role.STUDENT) return;
        boolean haEntratAbans = auditLog.jaHaFet(user.getId(), "LOGIN_OK");
        boolean adrecaConeguda = auditLog.jaHaFetDes(user.getId(), "LOGIN_OK", ip);
        auditLog.log(user.getId(), "LOGIN_OK", user.getRole().name());
        if (user.getRole() == com.examplatform.domain.model.Role.ADMIN && haEntratAbans && !adrecaConeguda) {
            auditLog.log(user.getId(), "LOGIN_NOVA_IP", ip);
        }
    }

    /**
     * Renova el token d'un usuari que encara el té vàlid: el frontend ho fa quan en queda poc,
     * perquè no caduqui a mig examen. Sense activitat, el token caduca igualment.
     */
    @PostMapping("/refresh")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public LoginResponse refresh(jakarta.servlet.http.HttpServletRequest request,
                                 @org.springframework.security.core.annotation.AuthenticationPrincipal User user) {
        String header = request.getHeader("Authorization");
        String actual = header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
        String nou;
        try {
            nou = jwtUtil.renova(actual, user);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "La sessió ha caducat: torna a entrar");
        }
        return new LoginResponse(nou, user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}

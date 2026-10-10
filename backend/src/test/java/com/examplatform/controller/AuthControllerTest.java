package com.examplatform.controller;

import com.examplatform.config.JwtUtil;
import com.examplatform.config.LoginRateLimiter;
import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    @Test
    void refresh_dona_un_token_nou_al_mateix_usuari() {
        JwtUtil jwt = mock(JwtUtil.class);
        User alumne = User.builder().id(UUID.randomUUID()).name("Anna").email("anna@x.cat").role(Role.STUDENT).build();
        when(jwt.renova("el-token-actual", alumne)).thenReturn("nou-token");
        AuthController c = new AuthController(mock(AuthenticationManager.class), jwt, mock(LoginRateLimiter.class), mock(com.examplatform.domain.service.AuditLogService.class));

        var peticio = new org.springframework.mock.web.MockHttpServletRequest();
        peticio.addHeader("Authorization", "Bearer el-token-actual");

        var r = c.refresh(peticio, alumne);

        assertThat(r.token()).isEqualTo("nou-token");
        assertThat(r.userId()).isEqualTo(alumne.getId());
        assertThat(r.role()).isEqualTo(Role.STUDENT);
    }

    @Test
    void refresh_d_una_sessio_que_ha_arribat_a_la_vida_maxima_respon_401() {
        JwtUtil jwt = mock(JwtUtil.class);
        User alumne = User.builder().id(UUID.randomUUID()).name("Anna").email("anna@x.cat").role(Role.STUDENT).build();
        when(jwt.renova("vell", alumne)).thenThrow(new io.jsonwebtoken.JwtException("vida màxima"));
        AuthController c = new AuthController(mock(AuthenticationManager.class), jwt, mock(LoginRateLimiter.class), mock(com.examplatform.domain.service.AuditLogService.class));
        var peticio = new org.springframework.mock.web.MockHttpServletRequest();
        peticio.addHeader("Authorization", "Bearer vell");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> c.refresh(peticio, alumne))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e).getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void refresh_sense_capcalera_d_autoritzacio_no_dona_cap_token() {
        JwtUtil jwt = mock(JwtUtil.class);
        User alumne = User.builder().id(UUID.randomUUID()).name("Anna").email("anna@x.cat").role(Role.STUDENT).build();
        when(jwt.renova("", alumne)).thenThrow(new IllegalArgumentException("buit"));
        AuthController c = new AuthController(mock(AuthenticationManager.class), jwt, mock(LoginRateLimiter.class), mock(com.examplatform.domain.service.AuditLogService.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> c.refresh(new org.springframework.mock.web.MockHttpServletRequest(), alumne))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    // ── Registre dels esdeveniments de seguretat ──────────────────────────────

    private org.springframework.mock.web.MockHttpServletRequest des(String ip) {
        var r = new org.springframework.mock.web.MockHttpServletRequest();
        r.setRemoteAddr(ip);
        return r;
    }

    private AuthenticationManager gestorQueDona(User usuari) {
        AuthenticationManager gestor = mock(AuthenticationManager.class);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(usuari, null, usuari.getAuthorities());
        when(gestor.authenticate(any())).thenReturn(auth);
        return gestor;
    }

    private static User persona(Role rol) {
        return User.builder().id(UUID.randomUUID()).name("X").email("x@x.cat").role(rol).build();
    }

    @Test
    void un_inici_de_sessio_fallit_queda_registrat_amb_el_compte_net() {
        var auditoria = mock(com.examplatform.domain.service.AuditLogService.class);
        AuthenticationManager gestor = mock(AuthenticationManager.class);
        when(gestor.authenticate(any())).thenThrow(new org.springframework.security.authentication.BadCredentialsException("no"));
        var limitador = mock(LoginRateLimiter.class);
        AuthController c = new AuthController(gestor, mock(JwtUtil.class), limitador, auditoria);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                c.login(new com.examplatform.dto.LoginRequest("  Anna\n@X.cat ", "mala"), des("10.0.0.5")))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);

        verify(auditoria).log(null, "LOGIN_FALLIT", "anna@x.cat");
        verify(limitador).registraFallada(eq("10.0.0.5"), any());
    }

    @Test
    void un_bloqueig_es_registra_un_sol_cop_per_compte_i_adreca() {
        var auditoria = mock(com.examplatform.domain.service.AuditLogService.class);
        var limitador = mock(LoginRateLimiter.class);
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS))
                .when(limitador).assertPermes(any(), any());
        AuthController c = new AuthController(mock(AuthenticationManager.class), mock(JwtUtil.class), limitador, auditoria);
        var peticio = new com.examplatform.dto.LoginRequest("prof@x.cat", "x");

        for (int i = 0; i < 5; i++) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> c.login(peticio, des("10.0.0.5")))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> c.login(peticio, des("10.0.0.6")));   // una altra adreça: un altre avís

        verify(auditoria, times(2)).log(null, "LOGIN_BLOQUEJAT", "prof@x.cat");
        verifyNoMoreInteractions(auditoria);
    }

    @Test
    void l_entrada_d_un_alumne_no_es_registra() {
        var auditoria = mock(com.examplatform.domain.service.AuditLogService.class);
        User alumne = persona(Role.STUDENT);
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.generateToken(alumne)).thenReturn("t");
        AuthController c = new AuthController(gestorQueDona(alumne), jwt, mock(LoginRateLimiter.class), auditoria);

        c.login(new com.examplatform.dto.LoginRequest("x@x.cat", "ok"), des("10.0.0.5"));

        verifyNoInteractions(auditoria);
    }

    @Test
    void l_entrada_d_un_professor_es_registra_sense_avis_d_adreca_nova() {
        var auditoria = mock(com.examplatform.domain.service.AuditLogService.class);
        User prof = persona(Role.PROFESSOR);
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.generateToken(prof)).thenReturn("t");
        when(auditoria.jaHaFet(prof.getId(), "LOGIN_OK")).thenReturn(true);   // ja havia entrat, però d'una altra adreça
        AuthController c = new AuthController(gestorQueDona(prof), jwt, mock(LoginRateLimiter.class), auditoria);

        c.login(new com.examplatform.dto.LoginRequest("x@x.cat", "ok"), des("10.0.0.5"));

        verify(auditoria).log(prof.getId(), "LOGIN_OK", "PROFESSOR");
        verify(auditoria, never()).log(any(), eq("LOGIN_NOVA_IP"), any());
    }

    @Test
    void un_administrador_que_entra_des_d_una_adreca_nova_queda_marcat() {
        var auditoria = mock(com.examplatform.domain.service.AuditLogService.class);
        User admin = persona(Role.ADMIN);
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.generateToken(admin)).thenReturn("t");
        when(auditoria.jaHaFet(admin.getId(), "LOGIN_OK")).thenReturn(true);
        when(auditoria.jaHaFetDes(admin.getId(), "LOGIN_OK", "10.0.0.5")).thenReturn(false);
        AuthController c = new AuthController(gestorQueDona(admin), jwt, mock(LoginRateLimiter.class), auditoria);

        c.login(new com.examplatform.dto.LoginRequest("x@x.cat", "ok"), des("10.0.0.5"));

        verify(auditoria).log(admin.getId(), "LOGIN_NOVA_IP", "10.0.0.5");
    }

    @Test
    void el_primer_inici_de_sessio_d_un_administrador_no_es_una_adreca_nova() {
        var auditoria = mock(com.examplatform.domain.service.AuditLogService.class);
        User admin = persona(Role.ADMIN);
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.generateToken(admin)).thenReturn("t");
        when(auditoria.jaHaFet(admin.getId(), "LOGIN_OK")).thenReturn(false);
        AuthController c = new AuthController(gestorQueDona(admin), jwt, mock(LoginRateLimiter.class), auditoria);

        c.login(new com.examplatform.dto.LoginRequest("x@x.cat", "ok"), des("10.0.0.5"));

        verify(auditoria).log(admin.getId(), "LOGIN_OK", "ADMIN");
        verify(auditoria, never()).log(any(), eq("LOGIN_NOVA_IP"), any());
    }

    @Test
    void un_administrador_que_entra_des_d_una_adreca_ja_coneguda_no_genera_avis() {
        var auditoria = mock(com.examplatform.domain.service.AuditLogService.class);
        User admin = persona(Role.ADMIN);
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.generateToken(admin)).thenReturn("t");
        when(auditoria.jaHaFet(admin.getId(), "LOGIN_OK")).thenReturn(true);
        when(auditoria.jaHaFetDes(admin.getId(), "LOGIN_OK", "10.0.0.5")).thenReturn(true);
        AuthController c = new AuthController(gestorQueDona(admin), jwt, mock(LoginRateLimiter.class), auditoria);

        c.login(new com.examplatform.dto.LoginRequest("x@x.cat", "ok"), des("10.0.0.5"));

        verify(auditoria, never()).log(any(), eq("LOGIN_NOVA_IP"), any());
    }
}

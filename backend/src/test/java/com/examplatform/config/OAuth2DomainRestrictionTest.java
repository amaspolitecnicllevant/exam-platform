package com.examplatform.config;

import com.examplatform.domain.model.ConfiguracioSistema;
import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import com.examplatform.domain.service.ConfiguracioService;
import com.examplatform.infrastructure.persistence.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2DomainRestrictionTest {

    @Mock UserRepository userRepository;
    @Mock JwtUtil jwtUtil;
    @Mock ConfiguracioService configuracioService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock Authentication authentication;
    @Mock OAuth2User oauth2User;

    OAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2SuccessHandler(userRepository, jwtUtil, configuracioService);
        ReflectionTestUtils.setField(handler, "frontendUrl",          "http://localhost:5173");
        ReflectionTestUtils.setField(handler, "defaultAllowedDomain", "politecnicllevant.cat");

        // per defecte: dominisOauth buit → usa defaultAllowedDomain
        ConfiguracioSistema cfg = new ConfiguracioSistema();
        cfg.setDominisOauth("");
        // lenient: el cas sense email surt abans de consultar la configuració
        lenient().when(configuracioService.get()).thenReturn(cfg);

        when(authentication.getPrincipal()).thenReturn(oauth2User);
        lenient().when(oauth2User.getAttribute("email_verified")).thenReturn(true);
        when(request.getRequestURI()).thenReturn("/login/oauth2/code/google");
    }

    @Test
    void domini_correcte_crea_o_recupera_usuari_i_redirigeix_amb_token() throws Exception {
        when(oauth2User.getAttribute("email")).thenReturn("alumne@politecnicllevant.cat");
        when(oauth2User.getAttribute("name")).thenReturn("Alumne Test");
        when(oauth2User.getAttribute("sub")).thenReturn("google-sub-123");

        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Alumne Test")
                .email("alumne@politecnicllevant.cat")
                .role(Role.STUDENT)
                .enabled(true)
                .build();
        when(userRepository.findByEmail("alumne@politecnicllevant.cat")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("jwt-token-abc");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(contains("/oauth-callback#token="));
        verify(response, never()).sendRedirect(contains("error=domain"));
    }

    @Test
    void domini_incorrecte_redirigeix_a_error_domain() throws Exception {
        when(oauth2User.getAttribute("email")).thenReturn("usuari@gmail.com");
        when(oauth2User.getAttribute("name")).thenReturn("Extern");
        when(oauth2User.getAttribute("sub")).thenReturn("sub-extern");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(contains("error=domain"));
        verify(userRepository, never()).save(any());
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void domini_incorrecte_amb_prefix_enganyos_es_bloqueja() throws Exception {
        // Assegurem que "politecnicllevant.cat.evil.com" no passa
        when(oauth2User.getAttribute("email")).thenReturn("a@politecnicllevant.cat.evil.com");
        when(oauth2User.getAttribute("name")).thenReturn("Atacant");
        when(oauth2User.getAttribute("sub")).thenReturn("sub-evil");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(contains("error=domain"));
    }

    @Test
    void compte_desactivat_redirigeix_a_error_disabled() throws Exception {
        when(oauth2User.getAttribute("email")).thenReturn("desactivat@politecnicllevant.cat");
        when(oauth2User.getAttribute("name")).thenReturn("Desactivat");
        when(oauth2User.getAttribute("sub")).thenReturn("sub-dis");

        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Desactivat")
                .email("desactivat@politecnicllevant.cat")
                .role(Role.STUDENT)
                .enabled(false)
                .build();
        when(userRepository.findByEmail("desactivat@politecnicllevant.cat")).thenReturn(Optional.of(user));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(contains("error=disabled"));
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void sense_email_redirigeix_a_error_no_email() throws Exception {
        when(oauth2User.getAttribute("email")).thenReturn(null);
        when(oauth2User.getAttribute("name")).thenReturn("Sense email");
        when(oauth2User.getAttribute("sub")).thenReturn("sub-noemail");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(contains("error=no_email"));
    }

    @Test
    void correu_no_verificat_no_entra() throws Exception {
        when(oauth2User.getAttribute("email")).thenReturn("alumne@politecnicllevant.cat");
        lenient().when(oauth2User.getAttribute("email_verified")).thenReturn(false);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(contains("error=no_email"));
        verify(jwtUtil, never()).generateToken(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void usuari_nou_es_crea_amb_rol_student() throws Exception {
        when(oauth2User.getAttribute("email")).thenReturn("nou@politecnicllevant.cat");
        when(oauth2User.getAttribute("name")).thenReturn("Nou Alumne");
        when(oauth2User.getAttribute("sub")).thenReturn("sub-nou");

        when(userRepository.findByEmail("nou@politecnicllevant.cat")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            return u;
        });
        when(jwtUtil.generateToken(any())).thenReturn("token-nou");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(userRepository).save(argThat(u -> u.getRole() == Role.STUDENT
                && "nou@politecnicllevant.cat".equals(u.getEmail())));
        verify(response).sendRedirect(contains("/oauth-callback"));
    }
}

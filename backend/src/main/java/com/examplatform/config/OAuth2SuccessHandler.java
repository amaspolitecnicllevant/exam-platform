package com.examplatform.config;

import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import com.examplatform.domain.service.ConfiguracioService;
import com.examplatform.infrastructure.persistence.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final ConfiguracioService configuracioService;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${oauth.allowed-domain:politecnicllevant.cat}")
    private String defaultAllowedDomain;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req,
                                        HttpServletResponse res,
                                        Authentication auth) throws IOException {
        OAuth2User oauth2User = (OAuth2User) auth.getPrincipal();

        String email    = oauth2User.getAttribute("email");
        String name     = oauth2User.getAttribute("name");
        String subject  = oauth2User.getAttribute("sub");
        String provider = extractProvider(req);

        if (email == null) {
            res.sendRedirect(frontendUrl + "/login?error=no_email");
            return;
        }

        // Només correus que el proveïdor ha verificat: si no, es podria entrar al compte d'un
        // altre usuari registrant el seu correu en un compte de Google no verificat
        Object verificat = oauth2User.getAttribute("email_verified");
        if (!(Boolean.TRUE.equals(verificat) || "true".equalsIgnoreCase(String.valueOf(verificat)))) {
            res.sendRedirect(frontendUrl + "/login?error=no_email");
            return;
        }

        if (!isDomainPermesa(email)) {
            res.sendRedirect(frontendUrl + "/login?error=domain");
            return;
        }

        User user = userRepository.findByEmail(email).orElseGet(() ->
            userRepository.save(User.builder()
                .name(name != null ? name : email)
                .email(email)
                .role(Role.STUDENT)
                .oauthProvider(provider)
                .oauthSubject(subject)
                .build())
        );

        if (!user.isEnabled()) {
            res.sendRedirect(frontendUrl + "/login?error=disabled");
            return;
        }

        // Si l'usuari existia però no tenia OAuth, vinculem
        if (user.getOauthSubject() == null) {
            user.setOauthProvider(provider);
            user.setOauthSubject(subject);
            userRepository.save(user);
        }

        String token = jwtUtil.generateToken(user);
        String redirect = frontendUrl + "/oauth-callback#token=" +
                URLEncoder.encode(token, StandardCharsets.UTF_8) +
                "&name="   + URLEncoder.encode(user.getName(),  StandardCharsets.UTF_8) +
                "&email="  + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8) +
                "&role="   + user.getRole().name() +
                "&userId=" + user.getId();

        res.sendRedirect(redirect);
    }

    private boolean isDomainPermesa(String email) {
        String dominisConfig = configuracioService.get().getDominisOauth();
        String dominisEffective = (dominisConfig != null && !dominisConfig.isBlank())
                ? dominisConfig
                : defaultAllowedDomain;
        List<String> dominis = Arrays.stream(dominisEffective.split("[,;\\s]+"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        String lower = email.toLowerCase();
        return dominis.stream().anyMatch(d -> lower.endsWith("@" + d));
    }

    private String extractProvider(HttpServletRequest req) {
        String uri = req.getRequestURI();
        if (uri.contains("google"))  return "google";
        if (uri.contains("github"))  return "github";
        if (uri.contains("azure"))   return "azure";
        return "unknown";
    }
}

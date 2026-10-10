package com.examplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    final LoginRateLimiter limiter = new LoginRateLimiter();

    @Test
    void bloqueja_el_compte_des_d_una_adreca_despres_de_massa_fallades() {
        for (int i = 0; i < LoginRateLimiter.MAX_FALLADES_PER_COMPTE_I_IP; i++) {
            limiter.assertPermes("10.0.0.5", "alumne@test.cat");
            limiter.registraFallada("10.0.0.5", "alumne@test.cat");
        }

        assertThatThrownBy(() -> limiter.assertPermes("10.0.0.5", "ALUMNE@test.cat "))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void bloqueja_la_ip_que_prova_molts_comptes() {
        for (int i = 0; i < LoginRateLimiter.MAX_FALLADES_PER_IP; i++) {
            limiter.registraFallada("10.0.0.7", "compte" + i + "@test.cat");
        }

        assertThatThrownBy(() -> limiter.assertPermes("10.0.0.7", "altre@test.cat"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void els_logins_correctes_no_consumeixen_intents() {
        for (int i = 0; i < 100; i++) {
            limiter.assertPermes("10.0.0.8", "prof@test.cat");   // sense registrar fallada
        }
        assertThatNoException().isThrownBy(() -> limiter.assertPermes("10.0.0.8", "prof@test.cat"));
    }

    @Test
    void un_compte_bloquejat_no_afecta_els_altres_de_la_mateixa_ip() {
        for (int i = 0; i < LoginRateLimiter.MAX_FALLADES_PER_COMPTE_I_IP; i++) {
            limiter.registraFallada("10.0.0.9", "victima@test.cat");
        }
        assertThatNoException().isThrownBy(() -> limiter.assertPermes("10.0.0.9", "company@test.cat"));
    }

    @Test
    void un_alumne_que_falla_molts_cops_no_deixa_fora_el_propietari_del_compte() {
        // L'atacant esgota els intents d'aquest compte des de la seva adreça...
        for (int i = 0; i < LoginRateLimiter.MAX_FALLADES_PER_COMPTE_I_IP; i++) {
            limiter.registraFallada("10.0.0.50", "professor@test.cat");
        }
        assertThatThrownBy(() -> limiter.assertPermes("10.0.0.50", "professor@test.cat"))
                .isInstanceOf(ResponseStatusException.class);

        // ...però el professor, des de la seva, hi pot entrar
        assertThatNoException().isThrownBy(() -> limiter.assertPermes("10.0.0.60", "professor@test.cat"));
    }

    @Test
    void un_atac_repartit_en_moltes_adreces_acaba_bloquejant_el_compte() {
        for (int i = 0; i < LoginRateLimiter.MAX_FALLADES_PER_COMPTE; i++) {
            limiter.registraFallada("10.1.0." + i, "professor@test.cat");
        }

        assertThatThrownBy(() -> limiter.assertPermes("10.1.1.1", "professor@test.cat"))
                .isInstanceOf(ResponseStatusException.class);
    }
}

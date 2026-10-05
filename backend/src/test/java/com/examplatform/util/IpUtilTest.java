package com.examplatform.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IpUtilTest {

    @Test
    void retorna_l_adreca_remota() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.1.10");

        assertThat(IpUtil.clientIp(req)).isEqualTo("192.168.1.10");
    }

    @Test
    void ignora_x_real_ip_enviada_pel_client() {
        // Si la petició no ve d'un proxy de confiança, Tomcat no substitueix l'adreça remota
        // i la capçalera l'ha pogut posar el mateix client per saltar-se la restricció d'aula
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("81.40.1.2");
        req.addHeader("X-Real-IP", "10.0.1.55");

        assertThat(IpUtil.clientIp(req)).isEqualTo("81.40.1.2");
    }

    @Test
    void ignora_x_forwarded_for() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("81.40.1.2");
        req.addHeader("X-Forwarded-For", "10.0.1.50, 10.0.0.1");

        assertThat(IpUtil.clientIp(req)).isEqualTo("81.40.1.2");
    }
}

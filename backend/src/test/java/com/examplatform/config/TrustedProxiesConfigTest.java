package com.examplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprova la regex de proxies de confiança tal com la resol Spring des d'application.yml.
 * Si fos incorrecta, o bé tots els alumnes semblarien venir de nginx (i la restricció d'aula els
 * rebutjaria), o bé qualsevol client podria falsejar la seva IP amb X-Real-IP.
 */
class TrustedProxiesConfigTest {

    private static Pattern proxiesDeConfianca() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        new YamlPropertySourceLoader().load("app", new ClassPathResource("application.yml"))
                .forEach(env.getPropertySources()::addLast);
        return Pattern.compile(env.getProperty("server.tomcat.remoteip.internal-proxies"));
    }

    @Test
    void nginx_a_la_xarxa_de_docker_i_la_mateixa_maquina_son_de_confianca() throws Exception {
        Pattern p = proxiesDeConfianca();
        assertThat(p.matcher("172.18.0.3").matches()).isTrue();
        assertThat(p.matcher("172.31.255.1").matches()).isTrue();
        assertThat(p.matcher("127.0.0.1").matches()).isTrue();
        assertThat(p.matcher("0:0:0:0:0:0:0:1").matches()).isTrue();
    }

    @Test
    void clients_de_la_xarxa_del_centre_o_d_internet_no_ho_son() throws Exception {
        Pattern p = proxiesDeConfianca();
        assertThat(p.matcher("192.168.1.20").matches()).isFalse();
        assertThat(p.matcher("10.0.1.5").matches()).isFalse();
        assertThat(p.matcher("172.32.0.1").matches()).isFalse();
        assertThat(p.matcher("81.40.1.2").matches()).isFalse();
        assertThat(p.matcher("172x18x0x3").matches()).isFalse();
    }
}

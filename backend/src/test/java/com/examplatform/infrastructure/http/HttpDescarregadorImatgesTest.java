package com.examplatform.infrastructure.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpDescarregadorImatgesTest {

    static final byte[] DADES = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};

    HttpServer servidor;
    String base;

    @BeforeEach
    void arrenca() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/ok", ex -> {
            ex.sendResponseHeaders(200, DADES.length);
            ex.getResponseBody().write(DADES);
            ex.close();
        });
        servidor.createContext("/redirigeix", ex -> {
            ex.getResponseHeaders().add("Location", "/ok");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        servidor.createContext("/bucle", ex -> {
            ex.getResponseHeaders().add("Location", "/bucle");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        servidor.createContext("/no-hi-es", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        servidor.createContext("/gran", ex -> {
            ex.sendResponseHeaders(200, 1000);
            ex.getResponseBody().write(new byte[1000]);
            ex.close();
        });
        servidor.start();
        base = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void atura() {
        servidor.stop(0);
    }

    private HttpDescarregadorImatges permissiu() {
        return new HttpDescarregadorImatges(true, 5);
    }

    @Test
    void baixa_el_contingut() {
        assertThat(permissiu().descarrega(base + "/ok", 1024)).isEqualTo(DADES);
    }

    @Test
    void segueix_les_redireccions() {
        assertThat(permissiu().descarrega(base + "/redirigeix", 1024)).isEqualTo(DADES);
    }

    @Test
    void talla_un_bucle_de_redireccions() {
        assertThatThrownBy(() -> permissiu().descarrega(base + "/bucle", 1024))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("massa redireccions");
    }

    @Test
    void informa_de_l_estat_si_no_es_200() {
        assertThatThrownBy(() -> permissiu().descarrega(base + "/no-hi-es", 1024))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("404");
    }

    @Test
    void rebutja_una_resposta_mes_gran_que_el_maxim() {
        assertThatThrownBy(() -> permissiu().descarrega(base + "/gran", 500))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pesa més");
    }

    @Test
    void per_defecte_no_visita_adreces_locals() {
        HttpDescarregadorImatges estricte = new HttpDescarregadorImatges(false, 5);

        assertThatThrownBy(() -> estricte.descarrega(base + "/ok", 1024))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> estricte.descarrega("http://localhost/ok", 1024))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void per_defecte_nomes_admet_http_i_https_i_els_ports_80_i_443() {
        HttpDescarregadorImatges estricte = new HttpDescarregadorImatges(false, 5);

        assertThatThrownBy(() -> estricte.valida(URI.create("file:///etc/passwd")))
                .hasMessageContaining("http");
        assertThatThrownBy(() -> estricte.valida(URI.create("ftp://exemple.cat/x.png")))
                .hasMessageContaining("http");
        assertThatThrownBy(() -> estricte.valida(URI.create("http://exemple.cat:22/x.png")))
                .hasMessageContaining("ports");
    }

    @Test
    void classifica_les_adreces_internes() throws Exception {
        for (String a : new String[] {"127.0.0.1", "10.100.79.224", "192.168.1.5", "172.16.0.1", "172.31.255.255",
                "169.254.169.254", "0.0.0.0", "100.64.0.1", "::1", "fe80::1", "fc00::1", "fd12:3456::1", "224.0.0.1"}) {
            assertThat(HttpDescarregadorImatges.esInterna(InetAddress.getByName(a))).as(a).isTrue();
        }
        for (String a : new String[] {"8.8.8.8", "1.1.1.1", "172.32.0.1", "100.128.0.1", "2606:4700:4700::1111"}) {
            assertThat(HttpDescarregadorImatges.esInterna(InetAddress.getByName(a))).as(a).isFalse();
        }
    }
}

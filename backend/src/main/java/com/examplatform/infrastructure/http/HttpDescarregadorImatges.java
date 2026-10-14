package com.examplatform.infrastructure.http;

import com.examplatform.domain.port.DescarregadorImatges;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Baixa imatges de la xarxa en importar un examen. Com que el servidor fa la petició en nom d'un usuari,
 * es protegeix contra l'ús per accedir a serveis interns (SSRF): només http/https pels ports 80 i 443,
 * cada redirecció es torna a validar i es rebutgen les adreces locals, privades o d'enllaç local
 * (inclosa la dels serveis de metadades dels núvols). Queda un risc residual de «DNS rebinding»
 * perquè el client torna a resoldre el nom en connectar; l'accés a la importació és només del professorat.
 */
@Component
public class HttpDescarregadorImatges implements DescarregadorImatges {

    private static final int MAX_REDIRECCIONS = 3;
    private static final Set<Integer> REDIRECCIONS = Set.of(301, 302, 303, 307, 308);

    /** Si és cert, es permeten adreces internes i qualsevol port (només per a proves o per a imatges d'una xarxa interna). */
    private final boolean xarxesPrivadesPermeses;
    private final Duration timeout;

    public HttpDescarregadorImatges(
            @Value("${exam.importacio-imatges.xarxes-privades-permeses:false}") boolean xarxesPrivadesPermeses,
            @Value("${exam.importacio-imatges.timeout-segons:10}") int timeoutSegons) {
        this.xarxesPrivadesPermeses = xarxesPrivadesPermeses;
        this.timeout = Duration.ofSeconds(timeoutSegons);
    }

    @Override
    public byte[] descarrega(String url, long maxBytes) {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)   // les redireccions es segueixen a mà, validant cada salt
                .connectTimeout(timeout)
                .build();
        URI uri = uriDe(url);
        try {
            for (int salts = 0; salts <= MAX_REDIRECCIONS; salts++) {
                valida(uri);
                HttpRequest req = HttpRequest.newBuilder(uri).GET().timeout(timeout)
                        .header("Accept", "image/png,image/jpeg,image/gif,image/webp")
                        .header("User-Agent", "exam-platform-importacio/1.0").build();
                HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                int estat = resp.statusCode();
                if (REDIRECCIONS.contains(estat)) {
                    resp.body().close();
                    String desti = resp.headers().firstValue("location")
                            .orElseThrow(() -> new IllegalArgumentException("redirecció sense destí"));
                    uri = uriDe(uri.resolve(desti).toString());
                    continue;
                }
                if (estat != 200) {
                    resp.body().close();
                    throw new IllegalArgumentException("el servidor ha respost " + estat);
                }
                long declarada = resp.headers().firstValueAsLong("content-length").orElse(-1);
                if (declarada > maxBytes) {
                    resp.body().close();
                    throw new IllegalArgumentException("pesa més de " + (maxBytes / 1024 / 1024) + " MB");
                }
                return llegeix(resp.body(), maxBytes);
            }
            throw new IllegalArgumentException("massa redireccions");
        } catch (IOException e) {
            throw new IllegalArgumentException("no s'hi ha pogut connectar (" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("descàrrega interrompuda");
        }
    }

    private static byte[] llegeix(InputStream in, long maxBytes) throws IOException {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) throw new IllegalArgumentException("pesa més de " + (maxBytes / 1024 / 1024) + " MB");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static URI uriDe(String url) {
        try {
            return new URI(url.strip());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("l'adreça no és vàlida");
        }
    }

    /** Valida l'esquema, el port i que el nom no apunti a una xarxa interna. */
    void valida(URI uri) {
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!esquema.equals("http") && !esquema.equals("https")) {
            throw new IllegalArgumentException("només s'admeten adreces http o https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("l'adreça no té servidor");
        }
        if (xarxesPrivadesPermeses) return;
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new IllegalArgumentException("només s'admeten els ports 80 i 443");
        }
        try {
            for (InetAddress a : InetAddress.getAllByName(uri.getHost())) {
                if (esInterna(a)) {
                    throw new IllegalArgumentException("l'adreça apunta a una xarxa interna i no es pot baixar des del servidor");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("no es troba el servidor «" + uri.getHost() + "»");
        }
    }

    /** Adreces que el servidor no ha de visitar en nom d'un usuari. */
    static boolean esInterna(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int primer = b[0] & 0xFF, segon = b[1] & 0xFF;
            return primer == 0                                   // 0.0.0.0/8
                    || (primer == 100 && segon >= 64 && segon <= 127);   // 100.64.0.0/10 (CGNAT)
        }
        return (b[0] & 0xFE) == 0xFC;                            // fc00::/7 (adreces locals úniques IPv6)
    }
}

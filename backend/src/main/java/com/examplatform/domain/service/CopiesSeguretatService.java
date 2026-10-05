package com.examplatform.domain.service;

import com.examplatform.dto.CopiesSeguretatDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Llegeix l'estat de l'última còpia de seguretat (estat.json, escrit pel servei "backup")
 * per mostrar-lo a l'administrador i avisar si falla o fa massa que no se'n fa cap.
 */
@Service
public class CopiesSeguretatService {

    /** Una còpia diària: més de 26 hores vol dir que se n'ha saltat alguna. */
    static final long MAX_HORES = 26;

    private final Path estat;
    private final ObjectMapper json = new ObjectMapper();
    private final Clock rellotge;

    @org.springframework.beans.factory.annotation.Autowired
    public CopiesSeguretatService(@Value("${backup.estat-path:/backups/estat.json}") String estat) {
        this(Path.of(estat), Clock.systemDefaultZone());
    }

    CopiesSeguretatService(Path estat, Clock rellotge) {
        this.estat = estat;
        this.rellotge = rellotge;
    }

    public CopiesSeguretatDto estat() {
        if (!Files.isReadable(estat)) {
            return new CopiesSeguretatDto(false, null, null, null, 0, 0, null, false, null, null, true,
                    "No hi ha cap còpia de seguretat registrada. Comprova que el servei «backup» està en marxa.");
        }
        try {
            JsonNode n = json.readTree(estat.toFile());
            OffsetDateTime data = OffsetDateTime.parse(n.path("data").asText());
            String resultat = n.path("resultat").asText();
            long hores = Duration.between(data, OffsetDateTime.now(rellotge)).toHours();
            JsonNode remot = n.path("remot");
            boolean remotConfigurat = remot.path("configurat").asBoolean(false);
            String remotResultat = remot.path("resultat").isTextual() ? remot.path("resultat").asText() : null;
            String remotMissatge = remot.path("missatge").asText(null);
            String motiu = null;
            if (!"ok".equals(resultat)) {
                motiu = "L'última còpia de seguretat ha fallat: " + n.path("missatge").asText();
            } else if (hores > MAX_HORES) {
                motiu = "Fa " + hores + " hores que no es fa cap còpia de seguretat.";
            } else if (remotConfigurat && !"ok".equals(remotResultat)) {
                motiu = "La còpia a la carpeta compartida ha fallat: " + remotMissatge;
            }
            return new CopiesSeguretatDto(true, data, resultat, n.path("missatge").asText(),
                    n.path("bytes").asLong(), n.path("retencioDies").asInt(), hores,
                    remotConfigurat, remotResultat, remotMissatge, motiu != null, motiu);
        } catch (IOException | RuntimeException e) {
            return new CopiesSeguretatDto(false, null, null, null, 0, 0, null, false, null, null, true,
                    "No s'ha pogut llegir l'estat de les còpies de seguretat.");
        }
    }
}

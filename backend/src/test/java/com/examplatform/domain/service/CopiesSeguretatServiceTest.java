package com.examplatform.domain.service;

import com.examplatform.dto.CopiesSeguretatDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class CopiesSeguretatServiceTest {

    @TempDir Path dir;
    private final Clock ara = Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneId.of("Europe/Madrid"));

    private CopiesSeguretatDto estat(String contingut) throws Exception {
        Path f = dir.resolve("estat.json");
        if (contingut != null) Files.writeString(f, contingut);
        return new CopiesSeguretatService(f, ara).estat();
    }

    @Test
    void copia_recent_i_correcta_no_avisa() throws Exception {
        CopiesSeguretatDto e = estat("""
                {"data": "2026-09-30T02:30:05+02:00", "resultat": "ok", "missatge": "Còpia correcta", "bytes": 72853, "retencioDies": 30}""");

        assertThat(e.disponible()).isTrue();
        assertThat(e.alerta()).isFalse();
        assertThat(e.antiguitatHores()).isEqualTo(9);
        assertThat(e.bytes()).isEqualTo(72853);
        assertThat(e.retencioDies()).isEqualTo(30);
    }

    @Test
    void copia_de_fa_mes_de_26_hores_avisa() throws Exception {
        CopiesSeguretatDto e = estat("""
                {"data": "2026-09-28T02:30:00+02:00", "resultat": "ok", "missatge": "", "bytes": 1, "retencioDies": 30}""");

        assertThat(e.alerta()).isTrue();
        assertThat(e.motiuAlerta()).contains("hores");
    }

    @Test
    void copia_fallida_avisa_amb_el_motiu() throws Exception {
        CopiesSeguretatDto e = estat("""
                {"data": "2026-09-30T02:30:00+02:00", "resultat": "error", "missatge": "pg_dump ha fallat", "bytes": 0, "retencioDies": 30}""");

        assertThat(e.alerta()).isTrue();
        assertThat(e.motiuAlerta()).contains("pg_dump ha fallat");
    }

    @Test
    void sense_cap_copia_avisa() throws Exception {
        CopiesSeguretatDto e = estat(null);

        assertThat(e.disponible()).isFalse();
        assertThat(e.alerta()).isTrue();
        assertThat(e.motiuAlerta()).contains("backup");
    }

    @Test
    void estat_malmes_avisa_sense_petar() throws Exception {
        CopiesSeguretatDto e = estat("{no és json");

        assertThat(e.disponible()).isFalse();
        assertThat(e.alerta()).isTrue();
    }

    @Test
    void copia_remota_correcta_no_avisa() throws Exception {
        CopiesSeguretatDto e = estat("""
                {"data": "2026-09-30T02:30:05+02:00", "resultat": "ok", "missatge": "", "bytes": 1, "retencioDies": 30,
                 "remot": {"configurat": true, "resultat": "ok", "missatge": "Còpia replicada i verificada"}}""");

        assertThat(e.alerta()).isFalse();
        assertThat(e.remotConfigurat()).isTrue();
        assertThat(e.remotResultat()).isEqualTo("ok");
    }

    @Test
    void copia_remota_fallida_avisa_encara_que_la_local_vagi_be() throws Exception {
        CopiesSeguretatDto e = estat("""
                {"data": "2026-09-30T02:30:05+02:00", "resultat": "ok", "missatge": "", "bytes": 1, "retencioDies": 30,
                 "remot": {"configurat": true, "resultat": "error", "missatge": "La carpeta compartida no està muntada"}}""");

        assertThat(e.alerta()).isTrue();
        assertThat(e.motiuAlerta()).contains("carpeta compartida").contains("no està muntada");
    }

    @Test
    void sense_copia_remota_configurada_no_avisa_per_aixo() throws Exception {
        CopiesSeguretatDto e = estat("""
                {"data": "2026-09-30T02:30:05+02:00", "resultat": "ok", "missatge": "", "bytes": 1, "retencioDies": 30,
                 "remot": {"configurat": false, "resultat": null, "missatge": ""}}""");

        assertThat(e.alerta()).isFalse();
        assertThat(e.remotConfigurat()).isFalse();
        assertThat(e.remotResultat()).isNull();
    }
}

package com.examplatform.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class HoraLocalTest {

    @AfterEach
    void restaura() { HoraLocal.setZona(ZoneId.of("Europe/Madrid")); }

    @Test
    void mostra_l_hora_utc_en_l_hora_del_centre_amb_horari_d_estiu_i_d_hivern() {
        assertThat(HoraLocal.format(LocalDateTime.of(2026, 10, 1, 7, 0), "HH:mm")).isEqualTo("09:00");   // UTC+2
        assertThat(HoraLocal.format(LocalDateTime.of(2026, 12, 1, 7, 0), "HH:mm")).isEqualTo("08:00");   // UTC+1
    }

    @Test
    void la_zona_es_configurable() {
        HoraLocal.setZona(ZoneId.of("Atlantic/Canary"));
        assertThat(HoraLocal.format(LocalDateTime.of(2026, 12, 1, 7, 0), "dd/MM HH:mm")).isEqualTo("01/12 07:00");
    }
}

package com.examplatform.config;

import com.examplatform.dto.ScheduleRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatesConfigTest {

    private final ObjectMapper om;

    DatesConfigTest() {
        Jackson2ObjectMapperBuilder b = new Jackson2ObjectMapperBuilder();
        new DatesConfig("Europe/Madrid").datesUtc().customize(b);
        om = b.build();
    }

    @Test
    void les_dates_surten_com_a_instant_utc_amb_z() throws Exception {
        String json = om.writeValueAsString(Map.of("d", LocalDateTime.of(2026, 10, 1, 7, 30)));
        assertThat(json).isEqualTo("{\"d\":\"2026-10-01T07:30:00Z\"}");
    }

    @Test
    void una_hora_amb_zona_es_passa_a_utc() throws Exception {
        // 09:00 a Madrid a l'octubre (UTC+2) = 07:00 UTC
        ScheduleRequest r = om.readValue("{\"scheduledAt\":\"2026-10-01T09:00:00+02:00\"}", ScheduleRequest.class);
        assertThat(r.scheduledAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 7, 0));

        r = om.readValue("{\"scheduledAt\":\"2026-10-01T07:00:00.000Z\"}", ScheduleRequest.class);
        assertThat(r.scheduledAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 7, 0));
    }

    @Test
    void sense_zona_s_enten_com_a_utc() throws Exception {
        ScheduleRequest r = om.readValue("{\"scheduledAt\":\"2026-10-01T07:00:00\"}", ScheduleRequest.class);
        assertThat(r.scheduledAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 7, 0));
    }

    @Test
    void anada_i_tornada_conserva_la_data() throws Exception {
        LocalDateTime d = LocalDateTime.of(2026, 3, 29, 1, 30, 15);
        String json = om.writeValueAsString(Map.of("scheduledAt", d));
        assertThat(om.readValue(json, ScheduleRequest.class).scheduledAt()).isEqualTo(d);
    }

    @Test
    void una_data_invalida_es_rebutja() {
        assertThatThrownBy(() -> om.readValue("{\"scheduledAt\":\"dema a les 9\"}", ScheduleRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidFormatException.class);
    }
}

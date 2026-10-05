package com.examplatform.config;

import com.examplatform.util.HoraLocal;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Dates de l'API. Internament són LocalDateTime en UTC; a l'API viatgen com a instants ISO amb "Z"
 * (p. ex. 2026-10-01T07:00:00Z), perquè el navegador les converteixi a l'hora local. Sense això,
 * el navegador interpretava com a locals hores que eren UTC (i al revés en programar un examen).
 */
@Configuration
public class DatesConfig {

    public DatesConfig(@Value("${app.zona-horaria:Europe/Madrid}") String zona) {
        HoraLocal.setZona(ZoneId.of(zona));
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer datesUtc() {
        return b -> b.serializerByType(LocalDateTime.class, new Serialitzador())
                .deserializerByType(LocalDateTime.class, new Deserialitzador());
    }

    public static class Serialitzador extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime v, JsonGenerator gen, SerializerProvider sp) throws IOException {
            gen.writeString(v.toInstant(ZoneOffset.UTC).toString());
        }
    }

    /** Accepta dates amb zona ("…Z", "…+02:00") i les passa a UTC; sense zona, s'entenen com a UTC. */
    public static class Deserialitzador extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) return null;
            try {
                return OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } catch (DateTimeParseException senseZona) {
                try {
                    return LocalDateTime.parse(text);
                } catch (DateTimeParseException e) {
                    return (LocalDateTime) ctx.handleWeirdStringValue(LocalDateTime.class, text,
                            "Data invàlida: %s", text);
                }
            }
        }
    }
}

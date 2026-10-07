package com.examplatform.dto;

import com.examplatform.domain.model.QuestionType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contingut complet d'una pregunta per crear-la o reescriure-la des de l'editor. Els camps que no
 * corresponen al tipus s'ignoren o es rebutgen amb les mateixes regles que la importació des de Markdown.
 *
 * @param opcions    només CHOICE: text de cada opció, sense la lletra (a, b, c, d per ordre)
 * @param posicio    només en crear: lloc (1 = primera) on s'insereix; null = al final
 */
public record QuestionEditRequest(
        QuestionType tipus,
        String enunciat,
        BigDecimal punts,
        List<String> opcions,
        String correctChoice,
        String modelResposta,
        String outputContains,
        String outputExact,
        String outputRegex,
        String testScript,
        String claus,
        String ra,
        String dificultat,
        Boolean barrejarOpcions,
        Boolean ambApunts,
        List<String> formatsPermesos,
        Integer posicio
) {}

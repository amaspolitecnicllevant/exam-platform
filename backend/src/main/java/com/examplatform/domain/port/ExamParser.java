package com.examplatform.domain.port;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.User;

public interface ExamParser {
    Exam parse(String mdContent, User createdBy);

    /**
     * Interpreta una sola pregunta (o secció) en sintaxi Markdown, amb les mateixes regles que la
     * importació però sense exigir que els punts de l'examen sumin 10. La pregunta torna sense examen.
     */
    com.examplatform.domain.model.Question parseQuestion(String mdPregunta);
}

package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Examen de prova per als tests d'exportació, construït sense BD. */
public final class FixtureExamen {

    public final Exam exam = Exam.builder().id(UUID.randomUUID()).title("Parcial UT1").durada(60)
            .questions(new ArrayList<>()).build();
    public final List<ExamSession> sessions = new ArrayList<>();
    public final List<Answer> respostes = new ArrayList<>();
    private int ordre = 0;

    public Question pregunta(QuestionType tipus, String punts, String ra) {
        Question q = Question.builder().id(UUID.randomUUID()).exam(exam).ordre(++ordre).tipus(tipus)
                .enunciat("Enunciat " + ordre).punts(new BigDecimal(punts)).ra(ra).build();
        exam.getQuestions().add(q);
        return q;
    }

    public Question test(String punts, String ra) {
        Question q = pregunta(QuestionType.CHOICE, punts, ra);
        q.setChoices("a) Primera\nb) Segona\nc) Tercera\nd) Quarta");
        q.setCorrectChoice("b");
        return q;
    }

    public ExamSession alumne(String nom, String email, SessionStatus estat, boolean comencat) {
        User u = User.builder().id(UUID.randomUUID()).name(nom).email(email).role(Role.STUDENT).build();
        ExamSession s = ExamSession.builder().id(UUID.randomUUID()).exam(exam).student(u).status(estat)
                .startedAt(comencat ? LocalDateTime.now().minusMinutes(30) : null).build();
        sessions.add(s);
        return s;
    }

    public ExamSession entregat(String nom, String email) {
        return alumne(nom, email, SessionStatus.SUBMITTED, true);
    }

    public Answer resposta(ExamSession s, Question q, String contingut, String manual) {
        Answer a = Answer.builder().id(UUID.randomUUID()).session(s).question(q).contingut(contingut)
                .manualScore(manual == null ? null : new BigDecimal(manual)).build();
        respostes.add(a);
        return a;
    }

    public DadesExamen dades() {
        return DadesExamen.de(exam, sessions, respostes);
    }
}

package com.examplatform.domain.service;

import com.examplatform.domain.model.QuestionType;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorreccioEnSegonPlaTest {

    @Mock AnswerRepository answerRepository;
    @Mock CorrectionService correctionService;
    @InjectMocks CorreccioEnSegonPla correccio;

    @Test
    void executa_totes_les_respostes_de_codi_de_la_sessio() {
        UUID sessionId = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        when(answerRepository.findIdsAmbContingut(eq(sessionId), any())).thenReturn(List.of(a1, a2));

        correccio.onSessioEntregada(new SessioEntregadaEvent(sessionId));

        verify(correctionService).execute(a1);
        verify(correctionService).execute(a2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void nomes_demana_els_tipus_executables() {
        UUID sessionId = UUID.randomUUID();
        when(answerRepository.findIdsAmbContingut(eq(sessionId), any())).thenReturn(List.of());

        correccio.onSessioEntregada(new SessioEntregadaEvent(sessionId));

        ArgumentCaptor<Collection<QuestionType>> tipus = ArgumentCaptor.forClass(Collection.class);
        verify(answerRepository).findIdsAmbContingut(eq(sessionId), tipus.capture());
        assertThat(tipus.getValue()).containsExactlyInAnyOrder(
                QuestionType.BASH_CMD, QuestionType.PS_CMD,
                QuestionType.BASH_SCRIPT, QuestionType.PS_SCRIPT, QuestionType.JAVA_PROG);
        verifyNoInteractions(correctionService);
    }

    @Test
    void una_resposta_que_falla_no_atura_la_resta() {
        UUID sessionId = UUID.randomUUID();
        UUID falla = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        when(answerRepository.findIdsAmbContingut(eq(sessionId), any())).thenReturn(List.of(falla, ok));
        when(correctionService.execute(falla)).thenThrow(new RuntimeException("docker caigut"));

        correccio.onSessioEntregada(new SessioEntregadaEvent(sessionId));

        verify(correctionService).execute(ok);
    }
}

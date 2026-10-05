package com.examplatform.domain.port;

import com.examplatform.domain.model.QuestionType;

import java.nio.file.Path;
import java.util.List;

public interface ScriptExecutor {

    record ExecutionResult(String output, int exitCode, long durationMs) {
        public boolean succeeded() { return exitCode == 0; }
    }

    ExecutionResult execute(QuestionType type, String code, List<Path> dataFiles);

    ExecutionResult executeWithTest(QuestionType type, String studentCode,
                                    String testScript, List<Path> dataFiles);

    default ExecutionResult execute(QuestionType type, String code) {
        return execute(type, code, List.of());
    }

    default ExecutionResult executeWithTest(QuestionType type, String studentCode, String testScript) {
        return executeWithTest(type, studentCode, testScript, List.of());
    }
}

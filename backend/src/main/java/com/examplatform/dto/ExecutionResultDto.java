package com.examplatform.dto;

import com.examplatform.domain.port.ScriptExecutor.ExecutionResult;

public record ExecutionResultDto(String output, int exitCode, long durationMs, boolean succeeded) {

    public static ExecutionResultDto from(ExecutionResult r) {
        return new ExecutionResultDto(r.output(), r.exitCode(), r.durationMs(), r.succeeded());
    }
}

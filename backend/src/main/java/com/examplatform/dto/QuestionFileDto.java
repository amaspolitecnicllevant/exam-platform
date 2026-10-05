package com.examplatform.dto;

import com.examplatform.domain.model.QuestionFile;

import java.util.UUID;

public record QuestionFileDto(UUID id, UUID questionId, String filename, String contentType, long fileSize) {

    public static QuestionFileDto from(QuestionFile f) {
        return new QuestionFileDto(f.getId(), f.getQuestion().getId(),
                f.getFilename(), f.getContentType(), f.getFileSize());
    }
}

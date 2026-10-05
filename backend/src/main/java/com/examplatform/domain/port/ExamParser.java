package com.examplatform.domain.port;

import com.examplatform.domain.model.Exam;
import com.examplatform.domain.model.User;

public interface ExamParser {
    Exam parse(String mdContent, User createdBy);
}

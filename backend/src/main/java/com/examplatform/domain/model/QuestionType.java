package com.examplatform.domain.model;

public enum QuestionType {
    TEXT, SHORT, LONG,
    CHOICE,
    BASH_CMD, PS_CMD,
    BASH_SCRIPT, PS_SCRIPT,
    JAVA_PROG,
    HTML_CSS,
    SECTION;

    public boolean isExecutable() {
        return this == BASH_CMD || this == PS_CMD
            || this == BASH_SCRIPT || this == PS_SCRIPT
            || this == JAVA_PROG;
    }

    public boolean isBash() {
        return this == BASH_CMD || this == BASH_SCRIPT;
    }

    public boolean isScript() {
        return this == BASH_SCRIPT || this == PS_SCRIPT;
    }

    public boolean isJava() {
        return this == JAVA_PROG;
    }
}

package com.raisetimeline.common.error;

import java.util.List;

/**
 * 業務ルールに反したときにサービス層から投げる例外。
 * {@link GlobalExceptionHandler} が、エラーの種類に応じた状態コードと本文に変える。
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldErrorDetail> errors;

    public ApiException(ErrorCode code) {
        this(code, List.of());
    }

    public ApiException(ErrorCode code, List<FieldErrorDetail> errors) {
        super(code.name());
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldErrorDetail> errors() {
        return errors;
    }
}

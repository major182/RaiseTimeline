package com.raisetimeline.common.error;

import java.net.URI;
import java.util.List;
import org.springframework.http.ProblemDetail;

/** エラーの本文（ProblemDetail ＋ {@code code}・{@code errors}。API 設計書 2.4）を作る。 */
public final class ProblemDetails {

    private ProblemDetails() {}

    public static ProblemDetail of(ErrorCode code, String path) {
        return of(code, List.of(), path);
    }

    public static ProblemDetail of(ErrorCode code, List<FieldErrorDetail> errors, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), code.message());
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code.name());
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }
}

package com.raisetimeline.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * すべてのエラーを、API 設計書 2.4 の形（ProblemDetail ＋ {@code code}・{@code errors}）で返す。
 *
 * <p>Spring MVC の標準の例外（JSON が読めない・URL がないなど）は、親クラスの
 * {@link ResponseEntityExceptionHandler} が状態コードを決め、ここで {@code code} を付け足す。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException e, HttpServletRequest request) {
        return ResponseEntity.status(e.code().status())
                .body(ProblemDetails.of(e.code(), e.errors(), request.getRequestURI()));
    }

    /** 想定外の誤り。中身は利用者に見せず、ログにだけ残す。 */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest request) {
        LOG.error("想定外の誤り: {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ProblemDetails.of(ErrorCode.INTERNAL_ERROR, request.getRequestURI()));
    }

    /** Bean Validation の誤り。注釈の message に書いた名前（{@link FieldErrorCode}）を日本語に置き換える。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> FieldErrorDetail.of(error.getField(), toFieldErrorCode(error.getDefaultMessage())))
                .distinct()
                .toList();
        return ResponseEntity.badRequest().body(ProblemDetails.of(ErrorCode.VALIDATION_FAILED, errors, path(request)));
    }

    /** Spring MVC の標準の例外に、状態コードに応じた {@code code} と日本語のメッセージを付ける。 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorCode code = status.value() == 404
                ? ErrorCode.NOT_FOUND
                : status.is4xxClientError() ? ErrorCode.VALIDATION_FAILED : ErrorCode.INTERNAL_ERROR;
        ProblemDetail problem = ProblemDetails.of(code, path(request));
        problem.setStatus(status.value()); // 405 などは元の状態コードのまま返す
        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    private static FieldErrorCode toFieldErrorCode(@Nullable String name) {
        try {
            return FieldErrorCode.valueOf(String.valueOf(name));
        } catch (IllegalArgumentException e) {
            // 注釈に message を書き忘れたときの保険。開発中に気づけるようログに残す
            LOG.warn("FieldErrorCode にない message です: {}", name);
            return FieldErrorCode.REQUIRED;
        }
    }

    private static String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}

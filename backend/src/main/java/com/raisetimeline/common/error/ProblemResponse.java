package com.raisetimeline.common.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * エラーの本文の形（API 設計書 2.4）。API 仕様書に載せるためだけの型で、実際の本文は {@link ProblemDetails} が作る。
 * ProblemDetail は項目を Map で持つため、仕様書に出せる形をここで書く。
 */
@Schema(name = "Problem", description = "エラーの本文（ProblemDetail（RFC 9457）＋ code・errors）")
public record ProblemResponse(
        @Schema(example = "Bad Request") String title,
        @Schema(example = "400") int status,
        @Schema(description = "画面にそのまま出せる日本語", example = "入力内容を確認してください") String detail,
        @Schema(example = "/api/auth/signup") String instance,
        @Schema(description = "エラーの種類。画面はこの値でメッセージを選ぶ") ErrorCode code,
        @Schema(
                        description = "入力欄ごとの誤り。400（VALIDATION_FAILED）と 409 のときだけ付く",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                List<FieldErrorDetail> errors) {}

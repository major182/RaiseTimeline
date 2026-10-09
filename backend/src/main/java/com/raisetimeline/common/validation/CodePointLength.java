package com.raisetimeline.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 文字列の長さを、Unicode のコードポイント（文字の番号）の数で確かめる（DB 設計書 D-7）。
 *
 * <p>標準の {@code @Size} は Java の char（UTF-16）の数で数えるため、絵文字が 2 文字に数えられてしまう。
 * コードポイントで数えると、画面・アプリ・DB（PostgreSQL の char_length）の 3 か所で同じ数になる。
 * null は「送られなかった」として誤りにしない（必須かどうかは @NotNull などで別に確かめる）。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CodePointLengthValidator.class)
public @interface CodePointLength {

    int min() default 0;

    int max();

    /** 誤りの種類（FieldErrorCode の名前）。 */
    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

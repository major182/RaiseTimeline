package com.raisetimeline.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * レコードの2つの項目が同じ値かを確かめる（パスワードとパスワード（確認）など）。
 * 誤りは {@link #confirmation()} の入力欄のものとして返す。確認の欄が空のときは、@NotBlank の誤りに任せて何もしない。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FieldsMatchValidator.class)
public @interface FieldsMatch {

    /** 元の項目の名前（例：password）。 */
    String field();

    /** 確認の項目の名前（例：passwordConfirmation）。 */
    String confirmation();

    String message() default "PASSWORD_MISMATCH";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

package com.raisetimeline.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link CodePointLength} の中身。 */
public class CodePointLengthValidator implements ConstraintValidator<CodePointLength, String> {

    private int min;
    private int max;

    @Override
    public void initialize(CodePointLength annotation) {
        min = annotation.min();
        max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = value.codePointCount(0, value.length());
        return length >= min && length <= max;
    }
}

package com.raisetimeline.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Objects;

/** {@link FieldsMatch} の中身。レコードの項目を名前で読み、2つの値を比べる。 */
public class FieldsMatchValidator implements ConstraintValidator<FieldsMatch, Record> {

    private String field;
    private String confirmation;
    private String message;

    @Override
    public void initialize(FieldsMatch annotation) {
        field = annotation.field();
        confirmation = annotation.confirmation();
        message = annotation.message();
    }

    @Override
    public boolean isValid(Record value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        Object confirmed = read(value, confirmation);
        if (confirmed == null || confirmed.toString().isEmpty() || Objects.equals(read(value, field), confirmed)) {
            return true;
        }
        // 誤りを、レコード全体ではなく確認の入力欄に付ける
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(confirmation)
                .addConstraintViolation();
        return false;
    }

    private static Object read(Record value, String name) {
        RecordComponent component = Arrays.stream(value.getClass().getRecordComponents())
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("項目がありません: " + name));
        try {
            return component.getAccessor().invoke(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

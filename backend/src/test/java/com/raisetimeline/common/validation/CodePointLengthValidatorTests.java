package com.raisetimeline.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/** 文字数をコードポイントで数える入力チェック（DB 設計書 D-7）。DB を使わない単体テスト。 */
class CodePointLengthValidatorTests {

    record Sample(@CodePointLength(min = 1, max = 3, message = "BODY_TOO_LONG") String text) {}

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(String text) {
        return validator.validate(new Sample(text)).isEmpty();
    }

    @Test
    void 範囲の端は通り外は誤りになる() {
        assertThat(valid("a")).isTrue();
        assertThat(valid("abc")).isTrue();
        assertThat(valid("")).isFalse();
        assertThat(valid("abcd")).isFalse();
    }

    @Test
    void 絵文字は1文字に数える() {
        String emoji = "😀"; // Java の char では 2 つ（サロゲートペア）
        assertThat(emoji.length()).isEqualTo(2);
        assertThat(valid(emoji.repeat(3))).isTrue();
        assertThat(valid(emoji.repeat(4))).isFalse();
    }

    @Test
    void 組み合わせた絵文字は部品の数で数える() {
        String family = "👨‍👩‍👧"; // 人 3 つ + つなぎの文字 2 つ = 5 コードポイント
        assertThat(valid(family)).isFalse();
    }

    @Test
    void nullは送られなかったとして通す() {
        assertThat(valid(null)).isTrue();
    }

    @Test
    void 誤りの種類はmessageに書いた名前() {
        assertThat(validator.validate(new Sample("abcd")))
                .singleElement()
                .satisfies(v -> assertThat(v.getMessage()).isEqualTo("BODY_TOO_LONG"));
    }
}

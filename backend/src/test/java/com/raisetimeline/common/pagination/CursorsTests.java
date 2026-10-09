package com.raisetimeline.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 一覧のカーソル（API 設計書 2.5）。DB を使わない単体テスト。 */
class CursorsTests {

    record SampleCursor(Instant at, long id) {
        SampleCursor {
            Objects.requireNonNull(at, "at");
        }
    }

    private final Cursors cursors = new Cursors(JsonMapper.builder().build());

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 作ったカーソルを読むと同じ値に戻る() {
        SampleCursor value = new SampleCursor(Instant.parse("2026-10-09T05:05:00.123456Z"), 42);
        assertThat(cursors.decode(cursors.encode(value), SampleCursor.class)).isEqualTo(value);
    }

    @Test
    void カーソルはURLにそのまま入れられる文字だけでできている() {
        String cursor = cursors.encode(new SampleCursor(Instant.now(), Long.MAX_VALUE));
        assertThat(cursor).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void カーソルがなければnullを返す() {
        assertThat(cursors.decode(null, SampleCursor.class)).isNull();
        assertThat(cursors.decode("", SampleCursor.class)).isNull();
    }

    @Test
    void Base64として読めないカーソルは400() {
        assertBadCursor("!!!not-base64!!!");
    }

    @Test
    void JSONとして読めないカーソルは400() {
        assertBadCursor(base64("{broken"));
    }

    @Test
    void 必要な値がないカーソルは400() {
        assertBadCursor(base64("{\"id\":1}"));
        assertBadCursor(base64("null"));
    }

    @Test
    void 件数が1ページより多ければ20件とカーソルを返す() {
        List<Integer> rows = IntStream.rangeClosed(1, Cursors.PAGE_SIZE + 1).boxed().toList();
        CursorPage<String> page = cursors.page(
                rows,
                n -> new SampleCursor(Instant.EPOCH, n),
                pageRows -> pageRows.stream().map(String::valueOf).toList());
        assertThat(page.items()).hasSize(Cursors.PAGE_SIZE).last().isEqualTo("20");
        // カーソルは返した最後の行（20 件目）を指す
        assertThat(cursors.decode(page.nextCursor(), SampleCursor.class).id()).isEqualTo(20);
    }

    @Test
    void 件数が1ページ以下ならカーソルはnull() {
        List<Integer> rows = IntStream.rangeClosed(1, Cursors.PAGE_SIZE).boxed().toList();
        CursorPage<Integer> page = cursors.page(rows, n -> new SampleCursor(Instant.EPOCH, n), r -> r);
        assertThat(page.items()).hasSize(Cursors.PAGE_SIZE);
        assertThat(page.nextCursor()).isNull();

        CursorPage<Integer> empty = cursors.page(List.<Integer>of(), n -> n, r -> r);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.nextCursor()).isNull();
    }

    private void assertBadCursor(String cursor) {
        assertThatThrownBy(() -> cursors.decode(cursor, SampleCursor.class))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}

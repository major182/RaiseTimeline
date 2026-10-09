package com.raisetimeline.common.pagination;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 一覧のカーソル（API 設計書 2.5、A-3）。
 *
 * <p>カーソルは「最後に返した行の並びの値」を JSON にして Base64URL にした文字列。
 * 画面は中身を読まずにそのまま送り返すだけなので、並び方の作りを変えても画面を直さずに済む。
 * 中身の形は API ごとに record で決める（例：フォローの一覧はフォローした時刻と利用者の ID）。
 */
@Component
public class Cursors {

    /** 1 回に返す件数（BR-53。変えられない）。 */
    public static final int PAGE_SIZE = 20;

    private final JsonMapper jsonMapper;

    public Cursors(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String encode(Object value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(jsonMapper.writeValueAsBytes(value));
    }

    /**
     * カーソルを読む。カーソルがなければ（最初の読み込み）null を返す。
     * 壊れたカーソル（Base64URL・JSON として読めない、必要な値がない）は 400 にする。
     */
    public <C> C decode(String cursor, Class<C> type) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            C value = jsonMapper.readValue(json, type);
            if (value == null) {
                throw new IllegalArgumentException("カーソルが空です");
            }
            return value;
        } catch (RuntimeException e) {
            // Base64 の誤り（IllegalArgumentException）、JSON の誤り（JacksonException）、値の欠け（record の検査）
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /**
     * 一覧を作る。rows には PAGE_SIZE + 1 件まで取っておき、21 件目があれば続きがあると判断する
     * （続きの有無を、件数を数える SQL なしで知るため）。
     *
     * @param rows DB から取った行（最大 PAGE_SIZE + 1 件）
     * @param cursorOf 行から、その行の次を指すカーソルの中身を作る
     * @param itemsOf 返す PAGE_SIZE 件の行から、一覧の中身を作る（まとめて変換し、N+1 にしない）
     */
    public <R, T> CursorPage<T> page(List<R> rows, Function<R, ?> cursorOf, Function<List<R>, List<T>> itemsOf) {
        boolean hasNext = rows.size() > PAGE_SIZE;
        List<R> pageRows = hasNext ? rows.subList(0, PAGE_SIZE) : rows;
        String next = hasNext ? encode(cursorOf.apply(pageRows.getLast())) : null;
        return new CursorPage<>(itemsOf.apply(pageRows), next);
    }
}

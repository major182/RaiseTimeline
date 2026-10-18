package com.raisetimeline;

import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.error.FieldErrorDetail;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** 単体テストで、サービスが投げた ApiException の中身（エラーの種類）を取り出す道具。 */
public final class ApiExceptions {

    private ApiExceptions() {}

    /** 処理を動かし、投げられた ApiException を返す。投げられなければテストを失敗させる。 */
    public static ApiException thrownBy(ThrowingCallable callable) {
        ApiException e = catchThrowableOfType(ApiException.class, callable);
        if (e == null) {
            throw new AssertionError("ApiException が投げられませんでした");
        }
        return e;
    }

    /** 投げられたエラーの種類（API の code）。 */
    public static ErrorCode codeOf(ThrowingCallable callable) {
        return thrownBy(callable).code();
    }

    /** 投げられた入力欄ごとの誤りの種類（API の errors[].code。FieldErrorCode の名前）。 */
    public static List<String> fieldCodesOf(ThrowingCallable callable) {
        return thrownBy(callable).errors().stream().map(FieldErrorDetail::code).toList();
    }
}

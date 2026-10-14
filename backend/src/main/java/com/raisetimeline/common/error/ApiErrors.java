package com.raisetimeline.common.error;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * その API に特有のエラー。API 仕様書（docs/api/openapi.yaml。API 設計書 5.1）の応答に出す。
 * 401（{@code UNAUTHENTICATED}）と 500 は、すべての API に共通なので書かない（OpenApiConfig が足す）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrors {

    ErrorCode[] value();
}

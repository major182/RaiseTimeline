package com.raisetimeline.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 画面が起動したときに呼び、CSRF トークンの Cookie（XSRF-TOKEN）を受け取る（API 設計書 2.2）。 */
@RestController
public class CsrfController {

    @GetMapping("/api/auth/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        // トークンは使われるまで作られない（遅延読み込み）。値を読んで作らせ、Cookie に書き出させる
        token.getToken();
        return ResponseEntity.noContent().build();
    }
}

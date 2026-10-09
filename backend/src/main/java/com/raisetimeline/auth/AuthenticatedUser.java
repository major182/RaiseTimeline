package com.raisetimeline.auth;

import java.security.Principal;

/**
 * ログインしている利用者。アクセストークン（JWT）の sub（利用者の ID）から作る。
 * 持つのは ID だけにする。表示名などは、使うたびに DB から読む（変更がすぐ反映されるように）。
 */
public record AuthenticatedUser(long id) implements Principal {

    @Override
    public String getName() {
        return Long.toString(id);
    }
}

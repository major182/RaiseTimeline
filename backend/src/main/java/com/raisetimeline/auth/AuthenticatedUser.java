package com.raisetimeline.auth;

import java.io.Serializable;
import java.security.Principal;

/**
 * ログインしている利用者として、セッションに保存する値。
 *
 * <p>持つのは ID だけにする。表示名などはセッションに入れず、使うたびに DB から読む（変更がすぐ反映されるように）。
 * セッションは DB に保存されるので、{@link Serializable} が必要。
 * {@link #getName()} の値は、Spring Session の principal_name 列に入る。
 */
public record AuthenticatedUser(long id) implements Principal, Serializable {

    @Override
    public String getName() {
        return Long.toString(id);
    }
}

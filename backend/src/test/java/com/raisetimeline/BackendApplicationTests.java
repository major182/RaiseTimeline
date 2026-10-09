package com.raisetimeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional // テストごとに巻き戻し、他のテストに登録したデータを残さない
class BackendApplicationTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void アプリが起動しマイグレーションでテーブルができる() {
        var tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains("users", "spring_session", "spring_session_attributes");
    }

    @Test
    void ユーザー名は大文字小文字を区別せずに重複を禁止する() {
        insertUser("raise_user", "a@example.com");
        assertThatThrownBy(() -> insertUser("RAISE_USER", "b@example.com"))
                .hasMessageContaining("users_username_lower_key");
    }

    @Test
    void ユーザー名の形式はDBの制約でも守る() {
        assertThatThrownBy(() -> insertUser("ab", "c@example.com")).hasMessageContaining("users_username_check");
    }

    private void insertUser(String username, String email) {
        jdbc.update(
                "INSERT INTO users (username, display_name, email, password_hash) VALUES (?, ?, ?, 'x')",
                username,
                username,
                email);
    }
}

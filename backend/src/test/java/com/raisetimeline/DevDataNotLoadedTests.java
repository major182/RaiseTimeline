package com.raisetimeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 開発用のテストデータは、開発のプロファイル以外（本番・テスト）では読み込まない（DB 設計書 7 章）。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DevDataNotLoadedTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 通常の設定ではテストデータのマイグレーションを実行しない() {
        int applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE script LIKE '%dev_data%'", Integer.class);
        assertThat(applied).isZero();
    }
}

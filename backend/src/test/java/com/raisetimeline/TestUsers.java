package com.raisetimeline;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** テスト用の利用者を API で登録し、ログインした状態の {@link ApiClient} を返す道具。 */
public final class TestUsers {

    private TestUsers() {}

    /**
     * ログインした利用者。
     *
     * @param client この利用者として API を呼ぶ道具（アクセストークンを覚えている）
     * @param id 利用者の ID
     */
    public record TestUser(ApiClient client, long id, String username) {}

    /** username で登録する。メールアドレスは username@example.com、パスワードは pass1234。 */
    public static TestUser signup(MockMvc mvc, String username) throws Exception {
        ApiClient client = new ApiClient(mvc);
        String body = client.post(
                        "/api/auth/signup",
                        """
                        {"email":"%s@example.com","password":"pass1234","passwordConfirmation":"pass1234","username":"%s"}
                        """
                                .formatted(username, username))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.user.id")).longValue();
        return new TestUser(client, id, username);
    }

    /** 利用者を DB に直接作る（BCrypt を通さないので速い。ログインはできない）。ID を返す。 */
    public static long insert(JdbcTemplate jdbc, String username, String displayName) {
        return jdbc.queryForObject(
                "INSERT INTO users (username, display_name, email, password_hash) VALUES (?, ?, ?, 'x') RETURNING id",
                Long.class,
                username,
                displayName,
                username + "@example.com");
    }

    public static long insert(JdbcTemplate jdbc, String username) {
        return insert(jdbc, username, username);
    }

    /** フォローを DB に直接作る。 */
    public static void follow(JdbcTemplate jdbc, long followerId, long followeeId) {
        jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", followerId, followeeId);
    }
}

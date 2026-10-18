package com.raisetimeline;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 発行した SQL の数を数える（N+1 の確認。NF-PE-02）。
 * JPA と JdbcTemplate の両方を数えるため、Hibernate の統計ではなく DataSource を包んで、接続から作った文（Statement）を数える。
 * テストのクラスに {@code @Import(SqlCounter.Config.class)} を付け、{@link #count(ThrowingRunnable)} で囲んだ間の数を取る。
 * 数は static に持つ（DataSource を包む BeanPostProcessor は、ほかの Bean より先に作られるため）。テストは 1 つずつ順に動く前提。
 */
public final class SqlCounter {

    /** SQL を 1 つ作る接続のメソッド。 */
    private static final Set<String> STATEMENT_METHODS = Set.of("prepareStatement", "prepareCall", "createStatement");

    private static final AtomicInteger STATEMENTS = new AtomicInteger();

    /** 囲んだ処理の間に作った SQL の数。 */
    public static int count(ThrowingRunnable action) throws Exception {
        STATEMENTS.set(0);
        action.run();
        return STATEMENTS.get();
    }

    /** 例外を投げられる処理（MockMvc の呼び出しなど）。 */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private SqlCounter() {}

    /** DataSource を、SQL を数えるものに包む設定。 */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        static BeanPostProcessor sqlCountingDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    return bean instanceof DataSource dataSource ? wrap(dataSource) : bean;
                }
            };
        }
    }

    private static DataSource wrap(DataSource target) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                    Object result = invoke(target, method, args);
                    return result instanceof Connection connection ? wrap(connection) : result;
                });
    }

    private static Connection wrap(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    if (STATEMENT_METHODS.contains(method.getName())) {
                        STATEMENTS.incrementAndGet();
                    }
                    return invoke(target, method, args);
                });
    }

    /** 包んだ先を呼ぶ。包んだ先の例外（SQLException など）は、そのまま投げ直す。 */
    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}

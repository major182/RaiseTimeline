package com.raisetimeline.config;

import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 画面（React のビルド結果）の配信（API 設計書 5 章、技術選定書 4.2）。
 *
 * <p>画面の URL（/users/raise_user など）は React Router が画面の中で切り替えるので、サーバーにはその名前のファイルがない。
 * 直接開いたり再読み込みしたりしたときに 404 にならないよう、ファイルがなければ index.html を返す。
 * ただし /api/・/actuator/・/media/ と Swagger UI（開発だけ）は画面ではないので、index.html を返さず、ふつうの 404（JSON）にする。
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    /** 画面のファイルの置き場所（ビルドした画面を Spring Boot の static に入れる）。 */
    private static final String STATIC = "classpath:/static/";

    /** index.html を返さない URL の始まり。 */
    private static final List<String> NOT_SCREENS =
            List.of("api/", "actuator/", "media/", "v3/api-docs", "swagger-ui");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource file = location.createRelative(resourcePath);
                        if (file.exists() && file.isReadable()) {
                            return file; // 画面のファイル（JavaScript・CSS・画像）はそのまま返す
                        }
                        if (NOT_SCREENS.stream().anyMatch(resourcePath::startsWith) || resourcePath.contains(".")) {
                            return null; // API などと、ないファイル（.js など）は 404
                        }
                        Resource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}

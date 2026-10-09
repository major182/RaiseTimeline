package com.raisetimeline.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * コントローラーに届く前（Spring Security のフィルター）で起きたエラーを、同じ形の JSON で書き出す。
 * フィルターの中では {@link GlobalExceptionHandler} が働かないため、ここで直接書く。
 */
@Component
public class ProblemDetailResponseWriter {

    private final JsonMapper jsonMapper;

    public ProblemDetailResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), ProblemDetails.of(code, request.getRequestURI()));
    }
}

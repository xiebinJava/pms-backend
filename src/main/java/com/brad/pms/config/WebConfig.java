package com.brad.pms.config;

import com.brad.pms.security.AuthInterceptor;
import com.brad.pms.security.JwtTokenProvider;
import com.brad.pms.mapper.UserMapper;
import com.brad.pms.mapper.AuthSessionMapper;
import com.brad.pms.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Web 配置：CORS + 鉴权拦截器
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;
    private final AuthSessionMapper authSessionMapper;
    private final AuthorizationService authorizationService;

    @Value("${pms.security.cors.allowed-origins:http://localhost:57979,http://127.0.0.1:57979}")
    private String allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(tokenProvider, userMapper, authSessionMapper, authorizationService))
                .addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        if (origins.isEmpty() || origins.stream().anyMatch("*"::equals)) {
            throw new IllegalStateException("pms.security.cors.allowed-origins 必须配置显式来源，不能使用 *");
        }
        registry.addMapping("/**")
                .allowedOriginPatterns(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

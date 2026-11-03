package com.polemon.github_manager.config;

import com.polemon.github_manager.controller.interceptor.TokenCheckInterceptor;
import com.polemon.github_manager.service.AuthService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration registering the application's interceptors. They
 * run after api-lib's identity check, so the caller's identity is known.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * The service used to read each user's GitHub token.
     */
    private final AuthService authService;

    public WebConfig(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers the {@link TokenCheckInterceptor} on every path except the ones
     * that must work without a GitHub token: the settings page (where it is
     * saved), the error page, the static files and the API fragments.
     *
     * @param registry the interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TokenCheckInterceptor(authService))
                .addPathPatterns("/**")
                .excludePathPatterns("/settings", "/settings/**", "/error", "/api/**",
                        "/css/**", "/js/**", "/svg/**", "/favicon.ico", "/favicon.svg");
    }

}

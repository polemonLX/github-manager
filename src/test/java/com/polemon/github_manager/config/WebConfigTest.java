package com.polemon.github_manager.config;

import com.polemon.github_manager.controller.interceptor.TokenCheckInterceptor;
import com.polemon.github_manager.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebConfigTest {

    @Test
    void tokenCheckCoversEveryPathButTheOnesThatWorkWithoutAToken() {
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);
        when(registry.addInterceptor(any(TokenCheckInterceptor.class))).thenReturn(registration);
        when(registration.addPathPatterns(any(String[].class))).thenReturn(registration);

        new WebConfig(mock(AuthService.class)).addInterceptors(registry);

        verify(registration).addPathPatterns("/**");
        verify(registration).excludePathPatterns("/settings", "/settings/**", "/error", "/api/**",
                "/css/**", "/js/**", "/svg/**", "/favicon.ico", "/favicon.svg");
    }

}

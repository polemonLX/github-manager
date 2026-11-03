package com.polemon.github_manager.controller.interceptor;

import com.polemon.api_lib.identity.Identity;
import com.polemon.api_lib.identity.web.IdentityInterceptor;
import com.polemon.github_manager.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Interceptor that gates the application behind the caller's own GitHub token,
 * sending a user who hasn't saved one yet to the settings page. The paths that
 * must work without a token (settings, error, static files, API) are excluded
 * where it is registered.
 */
@Slf4j
public class TokenCheckInterceptor implements HandlerInterceptor {

    /**
     * Where a user without a token is sent.
     */
    static final String SETTINGS_PATH = "/settings";

    /**
     * The service used to read the caller's GitHub token.
     */
    private final AuthService authService;

    public TokenCheckInterceptor(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Lets the request through when the caller has a GitHub token; otherwise
     * redirects to the settings page. HTMX requests get an {@code HX-Redirect}
     * header instead, so the whole page changes rather than a fragment.
     *
     * @param request  the incoming HTTP request, carrying api-lib's {@link Identity}
     * @param response the outgoing HTTP response
     * @param handler  the handler that would process the request
     * @return {@code true} if the request should proceed, {@code false} if it was redirected
     * @throws IOException if the redirect fails
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Identity identity = (Identity) request.getAttribute(IdentityInterceptor.ATTRIBUTE);
        if (identity == null || authService.getGithubToken(identity.userId()) != null)
            return true;

        log.debug("User {} has no GitHub token: {} redirected to {}", identity.userId(), request.getRequestURI(), SETTINGS_PATH);
        if ("true".equals(request.getHeader("HX-Request")))
            response.setHeader("HX-Redirect", SETTINGS_PATH);
        else
            response.sendRedirect(SETTINGS_PATH);
        return false;
    }

}

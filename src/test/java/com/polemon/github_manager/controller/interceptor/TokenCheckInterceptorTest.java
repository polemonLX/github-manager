package com.polemon.github_manager.controller.interceptor;

import com.polemon.api_lib.identity.Identity;
import com.polemon.api_lib.identity.web.IdentityInterceptor;
import com.polemon.github_manager.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenCheckInterceptorTest {

    private final AuthService authService = mock(AuthService.class);
    private final TokenCheckInterceptor interceptor = new TokenCheckInterceptor(authService);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void aUserWithATokenGoesThrough() throws IOException {
        when(authService.getGithubToken("u1")).thenReturn("t1");

        assertTrue(interceptor.preHandle(request("u1"), response, new Object()));
        assertNull(response.getRedirectedUrl());
    }

    @Test
    void aUserWithoutATokenIsSentToTheSettingsPage() throws IOException {
        assertFalse(interceptor.preHandle(request("u1"), response, new Object()));
        assertEquals("/settings", response.getRedirectedUrl());
    }

    @Test
    void anHtmxRequestWithoutATokenGetsAnHxRedirect() throws IOException {
        MockHttpServletRequest request = request("u1");
        request.addHeader("HX-Request", "true");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals("/settings", response.getHeader("HX-Redirect"));
        assertNull(response.getRedirectedUrl());
    }

    @Test
    void aRequestWithoutIdentityIsLeftToApiLib() throws IOException {
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/dashboard"), response, new Object()));
    }

    private static MockHttpServletRequest request(String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
        request.setAttribute(IdentityInterceptor.ATTRIBUTE, new Identity(userId, "polemon", false));
        return request;
    }

}

package com.polemon.github_manager.controller.web;

import com.polemon.github_manager.exceptions.InvalidTokenException;
import com.polemon.github_manager.model.dto.PullRequestDTO;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.service.AuthService;
import com.polemon.github_manager.service.RepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The pull requests, settings and issues pages.
 */
class OtherPagesControllerTest {

    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PullRequestsController(repositoryService),
                    new SettingsController(authService, repositoryService), new IssuesController())
            .setCustomArgumentResolvers(new FixedIdentity())
            .build();

    @Test
    void pullRequestsListOnlyRepositoriesWithOpenOnes() throws Exception {
        RepositoryDTO withPullRequest = new RepositoryDTO("polemonLX", "auth");
        withPullRequest.setOpenPullRequests(List.of(new PullRequestDTO()));
        when(repositoryService.getRepositories("u1")).thenReturn(List.of(withPullRequest, new RepositoryDTO("polemonLX", "portal")));

        mvc.perform(get("/pull-requests"))
                .andExpect(status().isOk())
                .andExpect(view().name("layouts/main"))
                .andExpect(model().attribute("content", "pages/pullRequests :: content"))
                .andExpect(model().attribute("repositories", List.of(withPullRequest)));
        mvc.perform(get("/pull-requests").header("HX-Request", "true"))
                .andExpect(view().name("pages/pullRequests :: content"));
    }

    @Test
    void pullRequestsSyncOneOrEveryRepositoryAndRefresh() throws Exception {
        mvc.perform(post("/pull-requests/sync").contentType(MediaType.APPLICATION_JSON).content("{\"owner\":\"polemonLX\",\"name\":\"auth\"}"))
                .andExpect(header().string("HX-Refresh", "true"))
                .andExpect(view().name("redirect:/pull-requests"));
        mvc.perform(post("/pull-requests/sync"))
                .andExpect(header().string("HX-Refresh", "true"));

        verify(repositoryService).syncPullRequest("u1", "polemonLX", "auth");
        verify(repositoryService).syncPullRequest("u1");
    }

    @Test
    void settingsShowTheTokenForm() throws Exception {
        mvc.perform(get("/settings"))
                .andExpect(view().name("layouts/main"))
                .andExpect(model().attribute("content", "pages/settings :: content"));
        mvc.perform(get("/settings").header("HX-Request", "true"))
                .andExpect(view().name("pages/settings :: content"));
    }

    @Test
    void settingsSaveATokenGitHubAccepts() throws Exception {
        mvc.perform(post("/settings").param("github-token", "ghp_good").header("HX-Request", "true"))
                .andExpect(view().name("pages/settings :: content"))
                .andExpect(model().attributeDoesNotExist("error"));

        verify(authService).setGithubToken("u1", "ghp_good");
    }

    @Test
    void settingsRefuseATokenGitHubRefuses() throws Exception {
        doThrow(new InvalidTokenException("GitHub token is invalid or expired")).when(authService).setGithubToken("u1", "ghp_bad");

        mvc.perform(post("/settings").param("github-token", "ghp_bad").header("HX-Request", "true"))
                .andExpect(view().name("pages/settings :: content"))
                .andExpect(model().attribute("error", "Invalid or expired GitHub token"));
    }

    @Test
    void settingsResetClearsTheTokenAndTheRepositories() throws Exception {
        mvc.perform(post("/settings/reset").header("HX-Request", "true"))
                .andExpect(view().name("pages/settings :: content"));

        var order = inOrder(authService, repositoryService);
        order.verify(authService).clear("u1");
        order.verify(repositoryService).clear("u1");
        verify(repositoryService, never()).syncAll();
    }

    @Test
    void issuesShowThePage() throws Exception {
        mvc.perform(get("/issues"))
                .andExpect(view().name("layouts/main"))
                .andExpect(model().attribute("content", "pages/issues :: content"));
        mvc.perform(get("/issues").header("HX-Request", "true"))
                .andExpect(view().name("pages/issues :: content"));
    }

}

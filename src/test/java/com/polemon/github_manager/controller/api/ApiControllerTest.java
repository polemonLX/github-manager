package com.polemon.github_manager.controller.api;

import com.polemon.api_lib.identity.Identity;
import com.polemon.github_manager.model.dto.BranchDTO;
import com.polemon.github_manager.model.dto.PullRequestDTO;
import com.polemon.github_manager.model.dto.RateLimitDTO;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.service.AuthService;
import com.polemon.github_manager.service.RepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final ApiController controller = new ApiController(authService, repositoryService);

    @Test
    void statsbarShowsTheCallersDataWithTheTokenMasked() {
        RepositoryDTO repository = new RepositoryDTO("polemonLX", "auth");
        repository.setOpenPullRequests(List.of(new PullRequestDTO()));
        repository.setFeatureBranches(List.of(new BranchDTO(), new BranchDTO()));
        RateLimitDTO rateLimit = new RateLimitDTO();
        when(repositoryService.getRepositories("u1")).thenReturn(List.of(repository));
        when(authService.getRateLimit("u1")).thenReturn(rateLimit);
        when(authService.getGithubToken("u1")).thenReturn("ghp_secret1234");
        Model model = new ConcurrentModel();

        String view = controller.getAllStats(new Identity("u1", "polemon", true), model);

        assertEquals("fragments/statsbar :: statsbar", view);
        @SuppressWarnings("unchecked")
        Map<String, Integer> stats = (Map<String, Integer>) model.getAttribute("stats");
        assertEquals(1, stats.get("repositoryCount"));
        assertEquals(1, stats.get("openPrCount"));
        assertEquals(2, stats.get("featureBranchCount"));
        assertSame(rateLimit, model.getAttribute("rateLimit"));
        assertEquals("****1234", model.getAttribute("githubToken"));
    }

    @Test
    void maskKeepsOnlyTheLastFourCharacters() {
        assertEquals("", ApiController.mask(null));
        assertEquals("", ApiController.mask(""));
        assertEquals("****abc", ApiController.mask("abc"));
        assertEquals("****6789", ApiController.mask("123456789"));
    }

}

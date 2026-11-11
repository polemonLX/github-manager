package com.polemon.github_manager.controller.web;

import com.polemon.github_manager.model.dto.ActionsRunningDTO;
import com.polemon.github_manager.model.dto.InternalWorkflowActiveDTO;
import com.polemon.github_manager.model.dto.InternalWorkflowStageActiveDTO;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.model.dto.WorkflowDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the dashboard's fragments, so template mistakes show up in the
 * build instead of at runtime.
 */
class DashboardPageTest {

    @Test
    void withoutRunsTheWorkflowPanelSaysHowToStartOne() {
        String html = render("active-workflow-content", Map.of("activeWorkflows", List.of(), "activeWorkflowRefreshIntervalSeconds", 7));

        assertTrue(html.contains("id=\"activeWorkflows\""));
        assertTrue(html.contains("hx-trigger=\"every 7s\""));
        assertTrue(html.contains("No workflows running"));
    }

    @Test
    void aRunningWorkflowShowsItsStagesAndCanBeStopped() {
        InternalWorkflowActiveDTO run = run("release", "running", true);
        run.setStartedAtText("10:00:00 11/11/2026");
        run.setStages(List.of(
                stage(0, "succeeded", false, null, null),
                stage(1, "running", true, "https://github.com/polemonLX/auth/actions/runs/8", null),
                stage(2, "waiting", false, null, null)));

        String html = squash(render("active-workflow-content", Map.of("activeWorkflows", List.of(run))));

        assertTrue(html.contains("<span class=\"workflow-name\">release</span>"));
        assertTrue(html.contains("3 stage(s)"));
        assertTrue(html.contains(" · started 10:00:00 11/11/2026"));
        assertFalse(html.contains(" · ended"));
        assertTrue(html.contains("class=\"aw-status aw-status-running\"> Running </span>"));
        assertTrue(html.contains("hx-post=\"/dashboards/active-workflow/stop?workflowName=release\""));
        assertTrue(html.contains("hx-confirm=\"Stop the workflow &#39;release&#39;? Pipelines already started keep running on GitHub.\""));
        assertFalse(html.contains("aw-dismiss"));
        assertTrue(html.contains("class=\"aw-stage aw-stage-succeeded\""));
        assertTrue(html.contains("class=\"aw-stage aw-stage-running\""));
        assertTrue(html.contains("class=\"aw-stage aw-stage-waiting\""));
        assertTrue(html.contains("<span class=\"wf-stage-index\">2</span>"));
        assertTrue(html.contains("· with previous"));
        assertTrue(html.contains("href=\"https://github.com/polemonLX/auth/actions/runs/8\""));
        assertTrue(html.contains("> Done </span>"));
        assertTrue(html.contains("> Waiting </span>"));
        assertFalse(html.contains("aw-error"));
    }

    @Test
    void endedRunsShowHowTheyEndedAndCanBeDismissed() {
        InternalWorkflowActiveDTO failed = run("release", "failed", false);
        failed.setFinishedAtText("10:05:00 11/11/2026");
        failed.setError("Pipeline release.yml on polemonLX/auth ended with failure");
        failed.setStages(List.of(stage(0, "failed", false, null, "Pipeline release.yml on polemonLX/auth ended with failure"),
                stage(1, "stopped", true, null, null)));
        InternalWorkflowActiveDTO finished = run("deploy", "succeeded", false);
        InternalWorkflowActiveDTO stopped = run("hotfix", "stopped", false);
        InternalWorkflowActiveDTO waiting = run("nightly", "waiting", true);

        String html = squash(render("active-workflow-content", Map.of("activeWorkflows", List.of(failed, finished, stopped, waiting))));

        assertTrue(html.contains("> Failed </span>"));
        assertTrue(html.contains("> Finished </span>"));
        assertTrue(html.contains("> Stopped </span>"));
        assertTrue(html.contains("> Waiting to start </span>"));
        assertTrue(html.contains(" · ended 10:05:00 11/11/2026"));
        assertTrue(html.contains("<p class=\"aw-error\">Pipeline release.yml on polemonLX/auth ended with failure</p>"));
        assertTrue(html.contains("<div class=\"aw-stage-error\">Pipeline release.yml on polemonLX/auth ended with failure</div>"));
        assertTrue(html.contains("hx-post=\"/dashboards/active-workflow/dismiss?workflowName=release\""));
        assertTrue(html.contains("aria-label=\"Remove deploy from the list\""));
        assertTrue(html.contains("hx-post=\"/dashboards/active-workflow/stop?workflowName=nightly\""));
    }

    @Test
    void actionDotsShowWhatRunsWhatIdlesAndWhatIsDisabled() {
        RepositoryDTO auth = new RepositoryDTO("polemonLX", "auth");
        auth.setActionsRunning(List.of(new ActionsRunningDTO(), new ActionsRunningDTO()));
        auth.setWorkflows(List.of(new WorkflowDTO("release", "u1", "active", true),
                new WorkflowDTO("ci", "u2", "active", false),
                new WorkflowDTO("old", "u3", "disabled_manually", false)));
        Map<String, Object> model = new HashMap<>();
        model.put("repositoryCount", 1);
        model.put("openPrCount", 0);
        model.put("branchCount", 0);
        model.put("actionsRunning", 2);
        model.put("repositories", List.of(auth));
        model.put("activeWorkflows", List.of());

        String html = squash(render("content", model));

        assertTrue(html.contains(">2 running</span>"));
        assertTrue(html.contains("<span class=\"lk-status-dot ok\" title=\"Running\"></span>"));
        assertTrue(html.contains("<span class=\"lk-status-dot\" title=\"Idle\"></span>"));
        assertTrue(html.contains("<span class=\"lk-status-dot off\" title=\"Disabled\"></span>"));
    }

    private static InternalWorkflowActiveDTO run(String name, String status, boolean active) {
        InternalWorkflowActiveDTO run = new InternalWorkflowActiveDTO();
        run.setWorkflowName(name);
        run.setStatus(status);
        run.setActive(active);
        run.setStageCount(3);
        run.setStages(List.of());
        return run;
    }

    private static InternalWorkflowStageActiveDTO stage(int index, String status, boolean parallel, String runUrl, String error) {
        InternalWorkflowStageActiveDTO stage = new InternalWorkflowStageActiveDTO();
        stage.setIndex(index);
        stage.setTitle("polemonLX/auth - Pipeline");
        stage.setDetails("pipeline: release.yml");
        stage.setStatus(status);
        stage.setParallel(parallel);
        stage.setRunUrl(runUrl);
        stage.setError(error);
        return stage;
    }

    private static String squash(String html) {
        return html.replaceAll("\\s+", " ");
    }

    private static String render(String fragment, Map<String, Object> model) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setRequestURI("/dashboard");
        WebContext context = new WebContext(JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, new MockHttpServletResponse()), Locale.US, new HashMap<>(model));
        return engine.process("pages/dashboard", Set.of(fragment), context);
    }

}

package com.polemon.github_manager.controller.web;

import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.model.dto.RepositoryGroupDTO;
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
 * Renders the repositories page's content fragment, so template mistakes show
 * up in the build instead of at runtime.
 */
class RepositoriesPageTest {

    private static String render(String selectedGroup, List<RepositoryGroupDTO> groups, List<RepositoryDTO> repositories) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setRequestURI("/repositories");
        Map<String, Object> model = new HashMap<>();
        model.put("selectedGroup", selectedGroup);
        model.put("groups", groups);
        model.put("repositories", repositories);
        WebContext context = new WebContext(JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, new MockHttpServletResponse()), Locale.US, model);
        return engine.process("pages/repositories", Set.of("content"), context);
    }

    private static RepositoryDTO repository(String name, List<String> groupIds) {
        RepositoryDTO repository = new RepositoryDTO("polemonLX", name);
        repository.setGroupIds(groupIds);
        return repository;
    }

    @Test
    void showsTheGroupsMenuWithAllSelected() {
        String html = render(null, List.of(new RepositoryGroupDTO("g1", "Apps")), List.of(repository("auth", List.of("g1", "g2"))));

        assertTrue(html.contains("class=\"group-item active\">All</a>"));
        assertTrue(html.contains("href=\"/repositories?group=g1\""));
        assertTrue(html.contains("hx-get=\"/repositories?group=g1\""));
        assertTrue(html.contains(">Apps</a>"));
        assertTrue(html.contains("data-groups=\"g1,g2\""));
        assertTrue(html.contains("class=\"btn edit-repo-btn\""));
        assertFalse(html.contains("btn btn-danger remove-repo-btn\" data-owner"));
        assertTrue(html.contains("name=\"groupIds\" class=\"edit-group-check\" value=\"g1\""));
    }

    @Test
    void marksTheSelectedGroupAndKeepsItInTheForms() {
        String html = render("g1", List.of(new RepositoryGroupDTO("g1", "Apps")), List.of());

        assertTrue(html.contains("class=\"group-item\">All</a>"));
        assertTrue(html.replaceAll("\\s+", " ").contains("class=\"group-item active\" hx-get=\"/repositories?group=g1\">Apps</a>"));
        assertTrue(html.contains("name=\"group\" value=\"g1\""));
        assertTrue(html.contains("No repositories in this group."));
    }

    @Test
    void withoutGroupsTheEditModalSaysHowToCreateOne() {
        String html = render(null, List.of(), List.of());

        assertTrue(html.contains("No groups yet."));
        assertTrue(html.contains("No repositories added yet."));
    }

}

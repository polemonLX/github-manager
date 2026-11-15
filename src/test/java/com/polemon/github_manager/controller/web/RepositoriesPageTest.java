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
    void eachGroupHasADeleteButtonKeepingTheFilter() {
        String html = render("g1", List.of(new RepositoryGroupDTO("g1", "Apps"), new RepositoryGroupDTO("g2", "O'Libs")), List.of())
                .replaceAll("\\s+", " ");

        assertTrue(html.contains("<button type=\"button\" class=\"group-delete\" hx-post=\"/repositories/groups/delete?groupId=g1&amp;group=g1\""));
        assertTrue(html.contains("hx-post=\"/repositories/groups/delete?groupId=g2&amp;group=g1\""));
        assertTrue(html.contains("hx-confirm=\"Delete the group &#39;Apps&#39;? Its repositories stay tracked.\""));
        assertTrue(html.contains("title=\"Delete group O&#39;Libs\""));
        assertTrue(html.contains("aria-label=\"Delete group Apps\""));
    }

    @Test
    void withoutAFilterTheDeleteButtonSendsAnEmptyGroup() {
        String html = render(null, List.of(new RepositoryGroupDTO("g1", "Apps")), List.of());

        assertTrue(html.contains("hx-post=\"/repositories/groups/delete?groupId=g1&amp;group=\""));
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
    void eachGroupCanBeRenamedFromAModal() {
        String html = render("g1", List.of(new RepositoryGroupDTO("g1", "Apps"), new RepositoryGroupDTO("g2", "O'Libs")), List.of())
                .replaceAll("\\s+", " ");

        assertTrue(html.contains("<button type=\"button\" class=\"group-rename\" title=\"Rename group O&#39;Libs\""));
        assertTrue(html.contains("data-group-id=\"g2\" data-group-name=\"O&#39;Libs\">&#9998;</button>"));
        assertTrue(html.contains("aria-label=\"Rename group Apps\""));
        assertTrue(html.contains("id=\"renameGroupModal\""));
        assertTrue(html.contains("hx-post=\"/repositories/groups/rename\""));
        assertTrue(html.contains("hx-target-400=\"#renameGroupErrorMessage\""));
        assertTrue(html.contains("<input type=\"hidden\" name=\"groupId\" id=\"renameGroupId\">"));
        assertTrue(html.indexOf("class=\"group-rename\"") < html.indexOf("class=\"group-delete\""));
    }

    @Test
    void eachRowShowsItsGroupsAsChipsThatFilter() {
        List<RepositoryGroupDTO> groups = List.of(new RepositoryGroupDTO("g1", "Apps"), new RepositoryGroupDTO("g2", "Libs"), new RepositoryGroupDTO("g3", "Ops"));
        String html = render("g2", groups, List.of(repository("auth", List.of("g3", "g1")), repository("api-lib", List.of())))
                .replaceAll("\\s+", " ");

        assertTrue(html.contains("<th>Groups</th>"));
        String auth = html.substring(html.indexOf("data-name=\"polemonlx/auth\""), html.indexOf("data-name=\"polemonlx/api-lib\""));
        assertTrue(auth.contains("hx-get=\"/repositories?group=g1\""));
        assertTrue(auth.contains("title=\"Show the group Apps\" hx-get=\"/repositories?group=g1\">Apps</a>"));
        assertTrue(auth.contains(">Ops</a>"));
        assertFalse(auth.contains(">Libs</a>"));
        assertTrue(auth.indexOf(">Apps</a>") < auth.indexOf(">Ops</a>"));
        String apiLib = html.substring(html.indexOf("data-name=\"polemonlx/api-lib\""));
        assertTrue(apiLib.contains("<td class=\"group-chips\"> </td>"));
        assertTrue(html.contains("colspan=\"10\""));
        assertFalse(html.contains("colspan=\"9\""));
    }

    @Test
    void theChipOfTheGroupShownIsMarked() {
        String html = render("g1", List.of(new RepositoryGroupDTO("g1", "Apps")), List.of(repository("auth", List.of("g1"))))
                .replaceAll("\\s+", " ");

        assertTrue(html.contains("class=\"group-chip active\""));
    }

    @Test
    void theRowsCanBeFilteredInTheBrowser() {
        String html = render(null, List.of(), List.of(repository("Auth", List.of()))).replaceAll("\\s+", " ");

        assertTrue(html.contains("<input type=\"search\" id=\"repoFilter\" class=\"form-control\""));
        assertTrue(html.contains("<tr class=\"repo-row\" data-name=\"polemonlx/auth\">"));
        assertTrue(html.contains("<tr id=\"repoNoMatch\" style=\"display: none;\">"));
        assertTrue(html.contains("No repository matches the filter."));
    }

    @Test
    void withoutRepositoriesThereIsNothingToFilter() {
        assertFalse(render(null, List.of(), List.of()).contains("id=\"repoFilter\""));
    }

    @Test
    void noneListsTheRepositoriesInNoGroupAboveAll() {
        String html = render("none", List.of(new RepositoryGroupDTO("g1", "Apps")), List.of()).replaceAll("\\s+", " ");

        assertTrue(html.contains("class=\"group-item group-none active\" title=\"Repositories in no group\">None</a>"));
        assertTrue(html.contains("hx-get=\"/repositories?group=none\""));
        assertTrue(html.indexOf(">None</a>") < html.indexOf(">All</a>"));
        assertTrue(html.contains("class=\"group-item\">All</a>"));
        assertTrue(html.contains("Every repository is in a group."));
        assertTrue(html.contains("name=\"group\" value=\"none\""));
    }

    @Test
    void withoutGroupsTheEditModalSaysHowToCreateOne() {
        String html = render(null, List.of(), List.of());

        assertTrue(html.contains("No groups yet."));
        assertTrue(html.contains("No repositories added yet."));
    }

}

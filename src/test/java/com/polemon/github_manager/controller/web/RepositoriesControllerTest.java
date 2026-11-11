package com.polemon.github_manager.controller.web;

import com.polemon.api_lib.identity.Identity;
import com.polemon.github_manager.exceptions.InvalidRepositoryException;
import com.polemon.github_manager.model.dto.RepositoryDTO;
import com.polemon.github_manager.model.dto.RepositoryGroupDTO;
import com.polemon.github_manager.service.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class RepositoriesControllerTest {

    private static final List<RepositoryGroupDTO> GROUPS = List.of(new RepositoryGroupDTO("g1", "Apps"), new RepositoryGroupDTO("g2", "libs"));
    private static final List<RepositoryDTO> REPOSITORIES = List.of(new RepositoryDTO("polemonLX", "auth"));

    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RepositoriesController(repositoryService))
            .setCustomArgumentResolvers(new FixedIdentity())
            .build();

    @BeforeEach
    void setUp() {
        when(repositoryService.getGroups("u1")).thenReturn(GROUPS);
        when(repositoryService.getRepositories(eq("u1"), any())).thenReturn(REPOSITORIES);
    }

    @Test
    void showsEveryRepositoryAsAFullPage() throws Exception {
        mvc.perform(get("/repositories"))
                .andExpect(status().isOk())
                .andExpect(view().name("layouts/main"))
                .andExpect(model().attribute("content", "pages/repositories :: content"))
                .andExpect(model().attribute("groups", GROUPS))
                .andExpect(model().attribute("selectedGroup", (Object) null))
                .andExpect(model().attribute("repositories", REPOSITORIES));

        verify(repositoryService).getRepositories("u1", null);
    }

    @Test
    void showsOneGroupAsAFragment() throws Exception {
        mvc.perform(get("/repositories").param("group", "g2").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/repositories :: content"))
                .andExpect(model().attribute("selectedGroup", "g2"));

        verify(repositoryService).getRepositories("u1", "g2");
    }

    @Test
    void anUnknownGroupShowsEveryRepository() throws Exception {
        mvc.perform(get("/repositories").param("group", "someone-elses").header("HX-Request", "true"))
                .andExpect(model().attribute("selectedGroup", (Object) null));

        verify(repositoryService).getRepositories("u1", null);
    }

    @Test
    void createsAGroupKeepingTheFilter() throws Exception {
        mvc.perform(post("/repositories/groups").param("name", "Apps").param("group", "g1").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/repositories :: content"))
                .andExpect(model().attribute("selectedGroup", "g1"));

        verify(repositoryService).createGroup("u1", "Apps");
    }

    @Test
    void anInvalidGroupIsA400WithTheReason() throws Exception {
        doThrow(new IllegalArgumentException("A group named 'Apps' already exists")).when(repositoryService).createGroup("u1", "Apps");

        mvc.perform(post("/repositories/groups").param("name", "Apps"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("pages/repositories :: addRepoError"))
                .andExpect(model().attribute("error", "A group named 'Apps' already exists"));
    }

    @Test
    void deletesAnotherGroupKeepingTheFilter() throws Exception {
        mvc.perform(post("/repositories/groups/delete").param("groupId", "g2").param("group", "g1").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/repositories :: content"))
                .andExpect(model().attribute("selectedGroup", "g1"))
                .andExpect(header().doesNotExist("HX-Push-Url"));

        verify(repositoryService).deleteGroup("u1", "g2");
        verify(repositoryService).getRepositories("u1", "g1");
    }

    @Test
    void deletingTheGroupShownGoesBackToEveryRepository() throws Exception {
        mvc.perform(post("/repositories/groups/delete").param("groupId", "g1").param("group", "g1").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedGroup", (Object) null))
                .andExpect(header().string("HX-Push-Url", "/repositories"));

        verify(repositoryService).deleteGroup("u1", "g1");
        verify(repositoryService).getRepositories("u1", null);
    }

    @Test
    void deletingAGroupWithoutAFilterShowsEveryRepository() throws Exception {
        mvc.perform(post("/repositories/groups/delete").param("groupId", "g2"))
                .andExpect(status().isOk())
                .andExpect(view().name("layouts/main"))
                .andExpect(header().doesNotExist("HX-Push-Url"));
        mvc.perform(post("/repositories/groups/delete").param("groupId", "g2").param("group", ""))
                .andExpect(model().attribute("selectedGroup", (Object) null))
                .andExpect(header().doesNotExist("HX-Push-Url"));

        verify(repositoryService, times(2)).deleteGroup("u1", "g2");
    }

    @Test
    void savesTheGroupsOfARepository() throws Exception {
        mvc.perform(post("/repositories/edit").param("owner", "polemonLX").param("name", "auth")
                        .param("groupIds", "g1", "g2").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/repositories :: content"));

        verify(repositoryService).setGroups("u1", "polemonLX", "auth", List.of("g1", "g2"));
    }

    @Test
    void noGroupCheckedSavesNoGroups() throws Exception {
        mvc.perform(post("/repositories/edit").param("owner", "polemonLX").param("name", "auth").header("HX-Request", "true"))
                .andExpect(status().isOk());

        verify(repositoryService).setGroups(eq("u1"), eq("polemonLX"), eq("auth"), isNull());
    }

    @Test
    void anInvalidEditIsA400WithTheReason() throws Exception {
        doThrow(new IllegalArgumentException("Unknown group")).when(repositoryService)
                .setGroups("u1", "polemonLX", "auth", List.of("x"));

        mvc.perform(post("/repositories/edit").param("owner", "polemonLX").param("name", "auth").param("groupIds", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("error", "Unknown group"));
    }

    @Test
    void addsARepositoryKeepingTheFilter() throws Exception {
        mvc.perform(post("/repositories/add").param("owner", "polemonLX").param("name", "auth").param("group", "g1")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedGroup", "g1"));

        verify(repositoryService).addRepository(eq("u1"), argThat(r -> r.getOwner().equals("polemonLX") && r.getName().equals("auth")));
    }

    @Test
    void anInvalidRepositoryIsA400WithTheReason() throws Exception {
        mvc.perform(post("/repositories/add").param("owner", "bad owner").param("name", "auth"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("pages/repositories :: addRepoError"));
        mvc.perform(post("/repositories/add").param("owner", " ").param("name", "auth"))
                .andExpect(status().isBadRequest());

        doThrow(new InvalidRepositoryException("Repository not found")).when(repositoryService).addRepository(eq("u1"), any());
        mvc.perform(post("/repositories/add").param("owner", "polemonLX").param("name", "ghost"))
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("error", "Repository not found"));
    }

    @Test
    void removesARepository() throws Exception {
        mvc.perform(delete("/repositories/remove").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"polemonLX\",\"name\":\"auth\"}").header("HX-Request", "true"))
                .andExpect(status().isOk());

        verify(repositoryService).removeRepository(eq("u1"), argThat(r -> r.getOwner().equals("polemonLX") && r.getName().equals("auth")));
    }

    @Test
    void syncsOneOrEveryRepository() throws Exception {
        mvc.perform(post("/repositories/sync").contentType(MediaType.APPLICATION_JSON).content("{\"owner\":\"polemonLX\",\"name\":\"auth\"}"))
                .andExpect(header().string("HX-Refresh", "true"));
        mvc.perform(post("/repositories/sync"))
                .andExpect(header().string("HX-Refresh", "true"));

        verify(repositoryService).sync("u1", "polemonLX", "auth");
        verify(repositoryService).sync("u1");
        verify(repositoryService, never()).syncAll();
    }

    /**
     * Stands in for api-lib's resolver: every request comes from user {@code u1}.
     */
    private static class FixedIdentity implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return Identity.class.equals(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return new Identity("u1", "polemon", false);
        }

    }

}

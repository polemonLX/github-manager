package com.polemon.github_manager.controller.web;

import com.polemon.api_lib.identity.Identity;
import com.polemon.github_manager.model.dto.InternalWorkflowDTO;
import com.polemon.github_manager.service.RepositoryService;
import com.polemon.github_manager.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class WorkflowsControllerTest {

    private static final String BODY = "{\"name\":\"release\",\"stages\":[{\"task\":\"COMMIT\"}]}";

    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowsController(repositoryService, workflowService))
            .setCustomArgumentResolvers(new FixedIdentity())
            .build();

    @Test
    void saveStoresTheWorkflowForTheCallerAndRendersTheFragment() throws Exception {
        mvc.perform(post("/workflows/save").header("HX-Request", "true").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/workflows :: content"));

        verify(workflowService).saveWorkflow(eq("u1"), any(InternalWorkflowDTO.class));
    }

    @Test
    void anInvalidWorkflowIsA400WithTheReason() throws Exception {
        doThrow(new IllegalArgumentException("A workflow named 'release' already exists"))
                .when(workflowService).saveWorkflow(eq("u1"), any(InternalWorkflowDTO.class));

        mvc.perform(post("/workflows/save").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("A workflow named 'release' already exists"));
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

package com.polemon.github_manager.controller.web;

import com.polemon.api_lib.identity.Identity;
import com.polemon.github_manager.exceptions.InvalidWorkflowException;
import com.polemon.github_manager.model.dto.InternalWorkflowDTO;
import com.polemon.github_manager.model.dto.InternalWorkflowStageDTO;
import com.polemon.github_manager.service.RepositoryService;
import com.polemon.github_manager.service.WorkflowService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Web controller serving the workflows page, listing registered GitHub Actions
 * workflows across all tracked repositories.
 */
@Slf4j
@Controller
@RequestMapping("/workflows")
public class WorkflowsController {

    /**
     * The service used to retrieve tracked repositories and their pipelines.
     */
    private final RepositoryService repositoryService;

    /**
     * The service used to persist, update, and retrieve internal workflows.
     */
    private final WorkflowService workflowService;

    public WorkflowsController(RepositoryService repositoryService, WorkflowService workflowService) {
        this.repositoryService = repositoryService;
        this.workflowService = workflowService;
    }

    /**
     * Builds the workflows page, listing all tracked repositories with their
     * associated workflows.
     *
     * @param identity the caller, from auth's identity token
     * @param model     the model to populate for rendering
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @return the workflows content fragment or the full page layout
     */
    @GetMapping
    public String showWorkflowsPage(Identity identity, Model model,
                                    @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        return answerRequest(identity, model, hxRequest);
    }

    /**
     * Validates and persists a new internal workflow, then re-renders the
     * workflows fragment. An invalid workflow (no name, no stages, or a name
     * the user already uses) is answered by {@link #rejectInvalidWorkflow}.
     *
     * @param identity the caller, from auth's identity token
     * @param model     the model to populate for rendering
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @param workflow  the workflow (name and stages) submitted from the builder
     * @return the workflows content fragment or the full page layout
     */
    @PostMapping("/save")
    public String saveWorkflow(Identity identity, Model model,
                               @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                               @RequestBody InternalWorkflowDTO workflow) {
        workflowService.saveWorkflow(identity.userId(), workflow);
        log.info("Saved workflow: {}", workflow.getName());

        return answerRequest(identity, model, hxRequest);
    }

    /**
     * Replaces an existing internal workflow (matched by name) with the submitted
     * version, then re-renders the workflows fragment.
     *
     * @param identity the caller, from auth's identity token
     * @param model           the model to populate for rendering
     * @param hxRequest       the {@code HX-Request} header, present when the request comes from HTMX
     * @param updatedWorkflow the workflow to persist in place of the existing one
     * @return the workflows content fragment or the full page layout
     */
    @PatchMapping("/update")
    public String updateWorkflow(Identity identity, Model model,
                                 @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                 @RequestBody InternalWorkflowDTO updatedWorkflow) {
        workflowService.updateWorkflow(identity.userId(), updatedWorkflow);
        log.info("Updated workflow: {}", updatedWorkflow.getName());

        return answerRequest(identity, model, hxRequest);
    }

    /**
     * Deletes the internal workflow with the given name, then re-renders the
     * workflows fragment.
     *
     * @param identity the caller, from auth's identity token
     * @param model        the model to populate for rendering
     * @param hxRequest    the {@code HX-Request} header, present when the request comes from HTMX
     * @param workflowName the name of the workflow to delete
     * @return the workflows content fragment or the full page layout
     */
    @DeleteMapping("/delete/{workflowName}")
    public String deleteWorkflow(Identity identity, Model model,
                                 @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                 @PathVariable String workflowName) {
        workflowService.deleteWorkflow(identity.userId(), workflowName);
        log.info("Deleted workflow: {}", workflowName);

        return answerRequest(identity, model, hxRequest);
    }

    /**
     * Starts an internal workflow, applying the per-stage runtime values entered
     * in the start dialog, then re-renders the workflows fragment.
     *
     * @param identity the caller, from auth's identity token
     * @param model        the model to populate for rendering
     * @param response     the HTTP response to set status codes
     * @param hxRequest    the {@code HX-Request} header, present when the request comes from HTMX
     * @param workflow  the workflow name and its stages, as submitted by the start dialog
     * @return the workflows content fragment or the full page layout
     */
    @PostMapping("/start")
    public String startWorkflow(Identity identity, Model model, HttpServletResponse response,
                                @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                @RequestBody InternalWorkflowDTO workflow) {
        log.info("Starting workflow: {}", workflow.getName());

        try {
            workflowService.startWorkflow(identity.userId(), workflow);

            return answerRequest(identity, model, hxRequest);
        } catch (InvalidWorkflowException e) {
            log.warn("Failed to start workflow: {}. Reason: {}", workflow.getName(), e.getMessage());
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("error", e.getMessage());
            return "pages/workflows :: startWorkflowError";
        }
    }

    /**
     * Stops the internal workflow with the given name, then re-renders the
     * workflows fragment. Execution is not yet implemented.
     *
     * @param identity the caller, from auth's identity token
     * @param model        the model to populate for rendering
     * @param hxRequest    the {@code HX-Request} header, present when the request comes from HTMX
     * @param workflowName the name of the workflow to stop
     * @return the workflows content fragment or the full page layout
     */
    @PostMapping("/stop")
    public String stopWorkflow(Identity identity, Model model,
                               @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                               @RequestParam String workflowName) {
        log.info("Stopping workflow: {}", workflowName);
        workflowService.stopWorkflow(identity.userId(), workflowName);

        return answerRequest(identity, model, hxRequest);
    }

    /**
     * Answers a workflow the builder can't save (save, update or delete) with
     * {@code 400} and {@code {"success": false, "error": "<reason>"}}, which the
     * builder shows to the user.
     *
     * @param e the validation failure
     * @return the {@code 400} answer with the reason
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> rejectInvalidWorkflow(IllegalArgumentException e) {
        log.warn("Rejected workflow: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }

    /**
     * Populates the model with the data every workflows render needs, then
     * returns either the bare content fragment (for HTMX requests) or the full
     * page layout wrapping it.
     *
     * @param identity  the caller, whose data the page shows
     * @param model     the model to populate
     * @param hxRequest the {@code HX-Request} header value, if present
     * @return the view name to render
     */
    private String answerRequest(Identity identity, Model model, String hxRequest) {
        model.addAttribute("repositories", repositoryService.getRepositories(identity.userId()));
        model.addAttribute("workflows", workflowService.getWorkflows(identity.userId()));
        model.addAttribute("stageTasks", InternalWorkflowStageDTO.StageTask.values());
        model.addAttribute("commitOperations", InternalWorkflowStageDTO.CommitOperation.values());
        model.addAttribute("runModes", InternalWorkflowStageDTO.RunMode.values());
        model.addAttribute("versionParts", InternalWorkflowStageDTO.VersionPart.values());

        if ("true".equals(hxRequest))
            return "pages/workflows :: content";

        model.addAttribute("content", "pages/workflows :: content");
        return "layouts/main";
    }

}

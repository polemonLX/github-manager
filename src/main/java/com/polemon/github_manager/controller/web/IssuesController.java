package com.polemon.github_manager.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Web controller serving the issues page.
 *
 * <p>Currently renders a placeholder page; issue tracking is not yet implemented.
 */
@Controller
@RequestMapping("/issues")
public class IssuesController {

    /**
     * Builds the issues page.
     *
     * @param model     the model to populate for rendering
     * @param hxRequest the {@code HX-Request} header, present when the request comes from HTMX
     * @return the issues content fragment or the full page layout
     */
    @GetMapping
    public String showIssuesPage(Model model,
                                 @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        return answerRequest(model, hxRequest);
    }

    /**
     * Renders either the bare content fragment (for HTMX requests) or the full
     * page layout wrapping it.
     *
     * @param model     the model to populate
     * @param hxRequest the {@code HX-Request} header value, if present
     * @return the view name to render
     */
    private String answerRequest(Model model, String hxRequest) {
        if ("true".equals(hxRequest))
            return "pages/issues :: content";

        model.addAttribute("content", "pages/issues :: content");
        return "layouts/main";
    }

}

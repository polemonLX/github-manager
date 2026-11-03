package com.polemon.github_manager.model.dto;

import lombok.*;

/**
 * Transfer object representing a published release (tag) of a repository.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ReleaseDTO {

    /** The release (tag) name, e.g. {@code 1.2.3}. */
    private String name;

}

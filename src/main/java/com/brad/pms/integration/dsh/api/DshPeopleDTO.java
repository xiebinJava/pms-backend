package com.brad.pms.integration.dsh.api;

/**
 * A single selectable account for the PMS Agent. Only the fields needed to pick
 * a person for a project field are exposed, so the Agent never receives phone
 * numbers, permission codes, or other account internals it has no use for.
 */
public record DshPeopleDTO(
        Long id,
        String displayName,
        String username,
        String email) {
}

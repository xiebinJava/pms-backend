package com.brad.pms.integration.dsh.api;

/**
 * The account the current PMS delegation acts as. Published with the capability
 * catalog so the Agent can resolve "我/我的" without asking the user which
 * account they are.
 */
public record DshViewerDTO(
        Long id,
        String displayName,
        String username,
        String email) {
}

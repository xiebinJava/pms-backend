package com.brad.pms.dto.request;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectUpdateCmdTest {

    @Test
    void carries_selected_members_and_followers() {
        ProjectUpdateCmd command = new ProjectUpdateCmd();
        command.setMemberIds(List.of(2L, 3L));
        command.setFollowerIds(List.of(4L, 5L));

        assertThat(command.getMemberIds()).containsExactly(2L, 3L);
        assertThat(command.getFollowerIds()).containsExactly(4L, 5L);
    }
}

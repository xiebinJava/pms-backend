package com.brad.pms.controller;

import com.brad.pms.dto.response.UserDTO;
import com.brad.pms.integration.dsh.api.DshPeopleDTO;
import com.brad.pms.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DshPeopleControllerTest {

    @Mock UserService userService;

    @Test
    void publishesOnlyActiveAccountsWithTheFieldsNeededToPickAPerson() {
        UserDTO active = user(31L, "张三", "ACTIVE");
        active.setEmail("zhangsan@pms.com");
        UserDTO disabled = user(32L, "李四", "DISABLED");
        when(userService.search("张")).thenReturn(List.of(active, disabled));

        List<DshPeopleDTO> people = new DshPeopleController(userService).people("张", null).getData();

        assertThat(people).singleElement().satisfies(person -> {
            assertThat(person.id()).isEqualTo(31L);
            assertThat(person.displayName()).isEqualTo("张三");
            assertThat(person.email()).isEqualTo("zhangsan@pms.com");
        });
    }

    @Test
    void boundsTheDirectoryResultSoTheAgentNeverReadsAnUnboundedRoster() {
        List<UserDTO> roster = new ArrayList<>();
        for (long id = 1; id <= 80; id++) {
            roster.add(user(id, "成员" + id, "ACTIVE"));
        }
        when(userService.search(null)).thenReturn(roster);

        DshPeopleController controller = new DshPeopleController(userService);

        assertThat(controller.people(null, null).getData()).hasSize(20);
        assertThat(controller.people(null, 5).getData()).hasSize(5);
        assertThat(controller.people(null, 500).getData()).hasSize(50);
    }

    private static UserDTO user(Long id, String displayName, String status) {
        UserDTO user = new UserDTO();
        user.setId(id);
        user.setUsername("user" + id);
        user.setDisplayName(displayName);
        user.setStatus(status);
        return user;
    }
}

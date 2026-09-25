package com.brad.pms.controller;

import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.response.UserDTO;
import com.brad.pms.integration.dsh.api.DshPeopleDTO;
import com.brad.pms.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Account directory for the PMS Agent. Project, member and follower fields are
 * chosen by person, so the Agent needs the same name-to-id lookup the PMS page
 * gets from its person picker.
 */
@RestController
@RequestMapping("/integration/dsh/v1")
@RequiredArgsConstructor
public class DshPeopleController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final UserService userService;

    @GetMapping("/people")
    public ResponseResult<List<DshPeopleDTO>> people(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<DshPeopleDTO> people = userService.search(keyword).stream()
                .filter(user -> user.getId() != null)
                .filter(DshPeopleController::isActive)
                .limit(size)
                .map(DshPeopleController::toPerson)
                .toList();
        return ResponseResult.success(people);
    }

    private static boolean isActive(UserDTO user) {
        return user.getStatus() == null || UserStatus.ACTIVE.name().equals(user.getStatus());
    }

    private static DshPeopleDTO toPerson(UserDTO user) {
        return new DshPeopleDTO(user.getId(), user.getDisplayName(), user.getUsername(), user.getEmail());
    }
}

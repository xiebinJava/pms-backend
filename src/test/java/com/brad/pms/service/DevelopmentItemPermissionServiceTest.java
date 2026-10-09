package com.brad.pms.service;

import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.workflow.DevelopmentItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DevelopmentItemPermissionServiceTest {
    @Mock AuthorizationService authorizationService;

    @Test
    void requirementMutationsUseRequirementWritePermission() {
        DevelopmentItemPermissionService service = new DevelopmentItemPermissionService(authorizationService);

        service.requireWrite(DevelopmentItemType.REQUIREMENT);

        verify(authorizationService).require(PermissionCode.REQUIREMENT_WRITE);
    }

    @Test
    void projectBackedMutationsUseProjectWritePermission() {
        DevelopmentItemPermissionService service = new DevelopmentItemPermissionService(authorizationService);

        service.requireWrite(DevelopmentItemType.TOPIC);
        service.requireWrite(DevelopmentItemType.STORY);

        verify(authorizationService, org.mockito.Mockito.times(2)).require(PermissionCode.PROJECT_WRITE);
    }
}

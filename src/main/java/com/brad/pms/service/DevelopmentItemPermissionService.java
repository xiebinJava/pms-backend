package com.brad.pms.service;

import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.workflow.DevelopmentItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Resolves the permission family for the shared topic/story/requirement workflow routes. */
@Service
@RequiredArgsConstructor
public class DevelopmentItemPermissionService {
    private final AuthorizationService authorizationService;

    public void requireWrite(DevelopmentItemType itemType) {
        authorizationService.require(itemType == DevelopmentItemType.REQUIREMENT
                ? PermissionCode.REQUIREMENT_WRITE
                : PermissionCode.PROJECT_WRITE);
    }
}

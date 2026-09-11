package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class ProjectPermissionsDTO {

    private boolean canManageProject;
    private boolean canManageMembers;
    private boolean canSetProjectManager;
    private boolean canAssignNodeOwner;
    private boolean canTerminateProject;
    private boolean canRestoreProject;
    private boolean canDeleteProject;
    private boolean canWriteComment;
}

package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodePermissionsDTO {

    private boolean canEdit;
    private boolean canManageTasks;
    private boolean canComplete;
    private boolean canRollback;
    private boolean readOnly;
}

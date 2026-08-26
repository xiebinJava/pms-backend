package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class TaskPermissionsDTO {

    private boolean canEdit;
    private boolean canMove;
    private boolean canDelete;
    private boolean readOnly;
}

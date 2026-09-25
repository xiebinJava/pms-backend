package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class ProjectTypeDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer status;
    private Integer sort;
    private Long defaultTemplateVersionId;
    private Long defaultTemplateId;
    private String defaultTemplateName;
}

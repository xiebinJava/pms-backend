package com.brad.pms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pms.workflow.defaults")
public class WorkflowDefaultProperties {
    private boolean writeEnabled;
    private String sourceDir = "src/main/resources/workflow-defaults";
}

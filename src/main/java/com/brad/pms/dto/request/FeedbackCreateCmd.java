package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedbackCreateCmd {

    @NotBlank(message = "反馈标题不能为空")
    @Size(max = 160, message = "反馈标题不能超过160个字符")
    private String title;

    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 5000, message = "反馈内容不能超过5000个字符")
    private String content;

    @NotNull(message = "反馈类型不能为空")
    private String feedbackType;

    private String priority;
    private Long projectId;
    private Long taskId;
    private Long nodeId;

    @Size(max = 80, message = "来源模块不能超过80个字符")
    private String contextModule;

    @Size(max = 1000, message = "来源地址不能超过1000个字符")
    private String sourceUrl;

    @Size(max = 80, message = "幂等请求号不能超过80个字符")
    private String clientRequestId;
}

package com.brad.pms.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class CommentCreateCmd {

    @NotBlank(message = "评论内容不能为空")
    private String content;

    private Long taskId;
}

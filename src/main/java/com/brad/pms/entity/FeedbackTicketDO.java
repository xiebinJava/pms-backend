package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feedback_ticket")
public class FeedbackTicketDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketNo;
    private String title;
    private String content;
    private String feedbackType;
    private String priority;
    private String status;
    private Long projectId;
    private Long taskId;
    private Long nodeId;
    private String contextModule;
    private String sourceUrl;
    private Long reporterId;
    private Long assigneeId;
    private String resolutionNote;
    private String clientRequestId;

    @Version
    private Integer version;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime closedAt;
}

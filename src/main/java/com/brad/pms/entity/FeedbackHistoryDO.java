package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feedback_history")
public class FeedbackHistoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String fromPriority;
    private String toPriority;
    private Long fromAssigneeId;
    private Long toAssigneeId;
    private String note;
    private Long operatorId;
    private String requestId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

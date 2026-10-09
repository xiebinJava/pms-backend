package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SystemVersionSaveCmd {
    @NotNull(message = "所属系统不能为空")
    private Long systemId;

    @NotBlank(message = "版本号不能为空")
    @Size(max = 64, message = "版本号不能超过 64 个字符")
    private String versionNo;

    @NotBlank(message = "版本名称不能为空")
    @Size(max = 200, message = "版本名称不能超过 200 个字符")
    private String versionName;

    private LocalDate plannedReleaseDate;

    @Size(max = 10000, message = "发布说明不能超过 10000 个字符")
    private String releaseNotes;

    private Long ownerId;
    private Integer version;
}

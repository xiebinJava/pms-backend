package com.brad.pms.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class ImportTemplateService {
    public byte[] organizationCsvTemplate() {
        return "组织编码,组织名称,组织类型编码,父组织编码,负责人英文名,排序\nHQ-DEMO,示例业务线,BG,HQ,,10\n".getBytes(StandardCharsets.UTF_8);
    }

    public byte[] userCsvTemplate() {
        return "中文名,英文名,邮箱,手机号,主组织编码,岗位编码,角色编码,直属上级英文名\n谢斌,Brad.Xie,brad@example.com,,HQ,EMPLOYEE,MEMBER,\n".getBytes(StandardCharsets.UTF_8);
    }
}

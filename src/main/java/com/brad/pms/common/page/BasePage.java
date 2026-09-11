package com.brad.pms.common.page;

import lombok.Data;

/**
 * 分页查询基类（分页查询基类）
 */
@Data
public class BasePage {

    private Integer currPage = 1;
    private Integer pageSize = 10;

    public Integer getCurrPage() {
        return currPage == null || currPage < 1 ? 1 : currPage;
    }

    public Integer getPageSize() {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }
}

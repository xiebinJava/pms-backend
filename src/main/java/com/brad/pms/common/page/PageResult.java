package com.brad.pms.common.page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页返回结构（分页返回结构）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private long total;
    private long currPage;
    private long pageSize;
    private long totalPage;
    private List<T> list;

    public static <T> PageResult<T> of(long total, long currPage, long pageSize, List<T> list) {
        long totalPage = pageSize == 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new PageResult<>(total, currPage, pageSize, totalPage, list);
    }
}

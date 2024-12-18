package com.my.base.common.result.domain.vo.request;

import cn.hutool.db.Page;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * @author <a href="https://github.com/zongzibinbin">abin</a>
 * @since 2023-03-19
 */
@Data
public class PageBaseReq {

    @Min(0)
    @Max(50)
    private Integer pageSize = 10;

    private Integer pageNo = 1;

    /**
     * 获取mybatisPlus的page
     *
     * @return
     */
    public Page plusPage() {
        return new Page(pageNo, pageSize);
    }
}

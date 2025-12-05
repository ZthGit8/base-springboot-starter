package com.my.base.web.response.domain.vo.request;

import cn.hutool.db.Page;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;


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

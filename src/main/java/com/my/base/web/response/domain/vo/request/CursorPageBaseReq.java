package com.my.base.web.response.domain.vo.request;

import cn.hutool.db.Page;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.ObjectUtils;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * @author <a href="https://github.com/zongzibinbin">abin</a>
 * @since 2023-03-19
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CursorPageBaseReq {

    @Min(0)
    @Max(100)
    private Integer pageSize = 10;
    /**
     *游标（初始为null，后续请求附带上次翻页的游标）
     */
    private String cursor;

    public Page plusPage() {
        return new Page(1, this.pageSize);
    }

    @JsonIgnore
    public Boolean isFirstPage() {
        return ObjectUtils.isEmpty(cursor);
    }
}

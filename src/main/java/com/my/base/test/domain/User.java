package com.my.base.test.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
*   @author ${zengtianhan}
*   @date 2025/2/18 18:28
*   @description: ${description}
*/
@Schema
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Schema(description="")
    private Integer id;

    @Schema(description="")
    private String name;

    @Schema(description="")
    private Integer age;

    @Schema(description="")
    private Date createDate;
}
package com.my.base.common.interceptor.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestInfo {
    private String traceId;
    private String requestIp;
    private long timestamp;
}

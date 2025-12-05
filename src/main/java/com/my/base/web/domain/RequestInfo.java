package com.my.base.web.domain;

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
    private String sign;
    private String nonce;
    private String method;
}

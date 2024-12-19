package com.my.base.common.future;

import com.my.base.common.exception.BusinessException;

@FunctionalInterface
public interface ThriftAsyncCall {
    void invoke() throws BusinessException;
}
package com.my.base.shared.async;

import com.my.base.error.BusinessException;

@FunctionalInterface
public interface ThriftAsyncCall {
    void invoke() throws BusinessException;
}
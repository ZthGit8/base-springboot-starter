package com.my.base.common.aspect;

import cn.hutool.json.JSONUtil;
import com.my.base.common.annotation.OtaApi;
import com.my.base.common.interceptor.context.RequestContext;
import com.my.base.common.interceptor.domain.RequestInfo;
import com.my.base.common.result.Result;
import com.my.base.common.utils.RedisUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author
 * @date 2025/1/3 14:26
 * @description: OTA接口切面，验证公共请求参数
 */
@Aspect
@Slf4j
@Component
public class OtaApiAspect {

    private static final String NONCE_KEY = "X-NONCE-";

    @Before("@annotation(com.my.base.common.annotation.OtaApi)")
    private Object doBefore(ProceedingJoinPoint joinPoint) throws Throwable {
        RequestInfo requestInfo = RequestContext.getRequestInfo();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OtaApi otaApi = method.getAnnotation(OtaApi.class);

        //获取请求参数
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        //验证请求头是否存在
        if (StringUtils.isEmpty(requestInfo.getSign()) || ObjectUtils.isEmpty(requestInfo.getTimestamp()) || StringUtils.isEmpty(requestInfo.getNonce())) {
            return Result.fail("sign,timestamp,nonce 不能为空");
        }
        /*
         * 1.重放验证
         * 判断timestamp时间戳与当前时间是否超过60s（过期时间根据业务情况设置）,如果超过了就提示签名过期。
         */
        long now = System.currentTimeMillis() / 1000;

        if (now - requestInfo.getTimestamp() > otaApi.keepTime()) {
            return Result.fail(String.format("签名过期：timestamp 与本服务时间相差超过[%d]s", otaApi.keepTime()));
        }

        //2. 判断nonce
        boolean nonceExists = RedisUtil.hasKey(NONCE_KEY + requestInfo.getNonce());
        if (nonceExists) {
            //请求重复
            return Result.fail("请求重复");
        } else {
            RedisUtil.set(NONCE_KEY + requestInfo.getNonce(), requestInfo.getNonce(), otaApi.nonceMaxTime());
        }

        SortedMap<String, String> paramMap = getParams(parameters, args);

        //3. 验证签名
        if (!verifySign(paramMap, requestInfo)) {
            return Result.fail("签名错误");
        }
        return joinPoint.proceed();
    }

    /**
     * 获取请求参数map
     *
     * @param parameters
     * @param args
     * @return
     */
    public SortedMap<String, String> getParams(Parameter[] parameters, Object[] args) {
        SortedMap<String, String> paramMap = new TreeMap<>();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String name = parameter.getName();
            Object arg = args[i];
            paramMap.put(name, arg.toString());
        }
        return paramMap;

    }

    /**
     * 验证签名
     * 验证算法：把 timestamp + JSONUtil.toJsonStr(SortedMap)合成字符串，然后MD5
     */
    @SneakyThrows
    public static boolean verifySign(SortedMap<String, String> map, RequestInfo requestInfo) {
        String params = requestInfo.getNonce() + requestInfo.getTimestamp() + JSONUtil.toJsonStr(map);
        return verifySign(params, requestInfo);
    }

    /**
     * 验证签名
     */
    public static boolean verifySign(String params, RequestInfo requestInfo) {
        if (StringUtils.isEmpty(params)) {
            return false;
        }
        String paramsSign = DigestUtils.md5DigestAsHex(params.getBytes()).toUpperCase();
        return requestInfo.getSign().equals(paramsSign);
    }
}

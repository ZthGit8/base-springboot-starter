package com.my.base.aop;

import cn.hutool.json.JSONUtil;
import com.my.base.shared.annotation.OtaApi;
import com.my.base.web.context.RequestContext;
import com.my.base.web.domain.RequestInfo;
import com.my.base.web.response.Result;
import com.my.base.shared.util.RedisUtil;
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

    /**
     * Redis中存储nonce的key前缀
     */
    private static final String NONCE_KEY = "X-NONCE-";

    /**
     * 处理带有@OtaApi注解的方法
     * 验证请求参数的合法性,包括:
     * 1. 必要的请求头参数校验
     * 2. 时间戳验证,防止重放攻击
     * 3. nonce验证,防止重复请求
     * 4. 签名验证,确保请求未被篡改
     *
     * @param joinPoint 切点
     * @return 处理结果
     * @throws Throwable 处理过程中的异常
     */
    @Before("@annotation(com.my.base.shared.annotation.OtaApi)")
    private Object doBefore(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前请求信息
        RequestInfo requestInfo = RequestContext.getRequestInfo();

        // 获取方法签名和注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OtaApi otaApi = method.getAnnotation(OtaApi.class);

        // 获取方法参数
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        // 1. 验证必要的请求头参数
        if (StringUtils.isEmpty(requestInfo.getSign()) 
            || ObjectUtils.isEmpty(requestInfo.getTimestamp()) 
            || StringUtils.isEmpty(requestInfo.getNonce())) {
            return Result.fail("sign,timestamp,nonce 不能为空");
        }

        // 2. 验证时间戳,防止重放攻击
        long now = System.currentTimeMillis() / 1000;
        if (now - requestInfo.getTimestamp() > otaApi.keepTime()) {
            return Result.fail(String.format("签名过期：timestamp 与本服务时间相差超过[%d]s", otaApi.keepTime()));
        }

        // 3. 验证nonce,防止重复请求
        String nonceKey = NONCE_KEY + requestInfo.getNonce();
        if (RedisUtil.hasKey(nonceKey)) {
            return Result.fail("请求重复");
        }
        RedisUtil.set(nonceKey, requestInfo.getNonce(), otaApi.nonceMaxTime());

        // 4. 验证签名
        SortedMap<String, String> paramMap = getParams(parameters, args);
        if (!verifySign(paramMap, requestInfo)) {
            return Result.fail("签名错误");
        }

        return joinPoint.proceed();
    }

    /**
     * 将方法参数转换为有序Map
     *
     * @param parameters 方法参数定义数组
     * @param args 方法参数值数组
     * @return 参数名和参数值的有序Map
     */
    private SortedMap<String, String> getParams(Parameter[] parameters, Object[] args) {
        SortedMap<String, String> paramMap = new TreeMap<>();
        for (int i = 0; i < parameters.length; i++) {
            paramMap.put(parameters[i].getName(), String.valueOf(args[i]));
        }
        return paramMap;
    }

    /**
     * 验证请求签名
     * 签名算法: MD5(nonce + timestamp + JSONUtil.toJsonStr(SortedMap))
     *
     * @param map 请求参数Map
     * @param requestInfo 请求信息
     * @return 签名是否有效
     */
    @SneakyThrows
    public static boolean verifySign(SortedMap<String, String> map, RequestInfo requestInfo) {
        String params = requestInfo.getNonce() + requestInfo.getTimestamp() + JSONUtil.toJsonStr(map);
        return verifySign(params, requestInfo);
    }

    /**
     * 验证请求签名
     *
     * @param params 待签名的参数字符串
     * @param requestInfo 请求信息
     * @return 签名是否有效
     */
    public static boolean verifySign(String params, RequestInfo requestInfo) {
        if (StringUtils.isEmpty(params)) {
            return false;
        }
        String paramsSign = DigestUtils.md5DigestAsHex(params.getBytes()).toUpperCase();
        return requestInfo.getSign().equals(paramsSign);
    }
}

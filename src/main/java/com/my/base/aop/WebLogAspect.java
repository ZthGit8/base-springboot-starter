package com.my.base.aop;

import cn.hutool.core.date.StopWatch;
import cn.hutool.json.JSONUtil;
import com.my.base.features.logging.RequestLogStorage;
import com.my.base.shared.util.ConversionUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import com.my.base.web.context.RequestContext;
import com.my.base.web.domain.RequestInfo;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 日志切面
 */
@Aspect
@Slf4j
@Component
@ConditionalOnProperty(name = "my.base.log-print-enable", havingValue = "true")
public class WebLogAspect {

    private final ThreadPoolExecutor threadPoolExecutor;

    private final ApplicationContext applicationContext;

    // 添加缓存：存储方法参数信息
    private final Map<Method, List<ParameterInfo>> methodParamCache = new ConcurrentHashMap<>();

    public WebLogAspect(@Qualifier("commonThreadPoolExecutor") ThreadPoolExecutor threadPoolExecutor, ApplicationContext applicationContext) {
        this.threadPoolExecutor = threadPoolExecutor;
        this.applicationContext = applicationContext;
    }

    /**
     * 接收到请求，记录请求内容
     * 所有controller包下所有的类的方法，都是切点
     * <p>
     * 如果ApiResult返回success=false，则打印warn日志；
     * warn日志只能打印在同一行，因为只有等到ApiResult结果才知道是success=false。
     * <p>
     * 如果ApiResult返回success=true，则打印info日志；
     * 特别注意：由于info级别日志已经包含了warn级别日志。如果开了info级别日志，warn就不会打印了。==
     */
    @Around("execution(* com..controller..*.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前请求的HttpServletRequest对象
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        // 获取请求方法
        String method = request.getMethod();
        // 获取请求URI
        String uri = request.getRequestURI();
        // 过滤掉HttpServletRequest和HttpServletResponse参数，记录其他参数名称和值
        List<Object> paramList = Stream.of(joinPoint.getArgs())
                .filter(args -> !(args instanceof HttpServletRequest))
                .filter(args -> !(args instanceof HttpServletResponse))
                .toList();
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        // 获取方法参数信息（使用缓存）
        List<ParameterInfo> parameterInfos = getMethodParameterInfos(signature.getMethod(), signature.getMethod().getParameters());

        // 创建一个列表来存储参数信息
        List<ParamInfo> paramInfoList = new ArrayList<>();
        for (int i = 0; i < parameterInfos.size(); i++) {
            ParameterInfo paramInfo = parameterInfos.get(i);
            // 将参数名和参数值组合并添加到列表中
            paramInfoList.add(new ParamInfo(paramInfo.paramType, paramInfo.parameter, paramList.get(i)));
        }

        // 把参数名和参数值组装成map
        Map<String, Object> paramMap = paramInfoList.stream().collect(Collectors.toMap(o -> o.getParameter().getName(), o ->ConversionUtil.convert(o.getValue(),o.getParamType())));
        // 将参数列表转换为JSON字符串
        String printParamStr = JSONUtil.toJsonStr(paramMap);
        // 获取请求信息
        RequestInfo requestInfo = RequestContext.getRequestInfo();
        // 将用户头部信息转换为JSON字符串
        String userHeaderStr = JSONUtil.toJsonStr(requestInfo);
        // 如果开启了info日志级别，打印请求信息
        if (log.isInfoEnabled()) {
            log.info("[{}][{}]【base:{}】【params:{}】", method, uri, userHeaderStr, printParamStr);
        }
        // 创建一个停表来记录方法执行时间
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        // 执行目标方法
        Object result = joinPoint.proceed();
        stopWatch.stop();
        // 计算方法执行时间
        long cost = stopWatch.getTotalTimeMillis();
        // 将结果转换为JSON字符串
        String printResultStr = JSONUtil.toJsonStr(result);
        // 如果开启了info日志级别，打印响应信息和执行时间
        if (log.isInfoEnabled()) {
            log.info("[{}]【response:{}】[cost:{}ms]", uri, printResultStr, cost);
        }
        // 异步将日志信息存储到数据库
        threadPoolExecutor.execute(()->{
            // 获取所有LogStorage实现类的实例
            Map<String, RequestLogStorage> beansOfType = applicationContext.getBeansOfType(RequestLogStorage.class);
            // 遍历所有LogStorage实例，调用save方法保存日志信息
            beansOfType.values().forEach(requestLogStorage -> requestLogStorage.save(requestInfo.getRequestIp(),JSONUtil.toJsonStr(paramMap)));
        });
        // 返回目标方法的执行结果
        return result;
    }

    /**
     * 获取方法参数信息（带缓存）
     * @param method 方法
     * @param parameters 参数数组
     * @return 参数信息列表
     */
    private List<ParameterInfo> getMethodParameterInfos(Method method, Parameter[] parameters) {
        return methodParamCache.computeIfAbsent(method, m -> {
            List<ParameterInfo> paramInfos = new ArrayList<>();
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                // 跳过HttpServletRequest和HttpServletResponse参数
                if (parameter.getType() == HttpServletRequest.class || parameter.getType() == HttpServletResponse.class) {
                    continue;
                }
                paramInfos.add(new ParameterInfo(parameter.getType(), parameter));
            }
            return paramInfos;
        });
    }

    @Data
    @AllArgsConstructor
    public static class ParamInfo {
        private Class<?> paramType;
        private Parameter parameter;
        private Object value;
    }

    /**
     * 参数信息类（用于缓存）
     */
    private static class ParameterInfo {
        private final Class<?> paramType;
        private final Parameter parameter;

        public ParameterInfo(Class<?> paramType, Parameter parameter) {
            this.paramType = paramType;
            this.parameter = parameter;
        }
    }
}

package com.my.base.common.service.manager;

import cn.hutool.extra.spring.SpringUtil;
import com.my.base.common.function.SerialBiFunction;
import com.my.base.common.result.Result;
import com.my.base.common.utils.LambdaUtil;
import lombok.extern.slf4j.Slf4j;


import java.lang.invoke.SerializedLambda;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 日志注解，能打日志
@Slf4j
public class ServiceManager {
    // 缓存初始化大小，6666够咱用了，不够再改
    private static final int INIT_COUNT = 6666;
    // 缓存Lambda对应的Service信息，key是Lambda，value是Service元数据
    private static final Map<SerialBiFunction<?,?,?>,LambdaMeta<?>>CACHE_LAMBDA;

    // 静态代码块，项目启动时就初始化缓存
    static {
        CACHE_LAMBDA = new ConcurrentHashMap<>(INIT_COUNT);
    }
    // 对外提供的调用方法：传Lambda（比如UserService::queryUser）和参数，返回结果
    @SuppressWarnings("unchecked")
    public static<T, U, R> Result<R> call(SerialBiFunction<T, U, R> fn, U param) {
        // 先检查：Lambda不能传空
        if (fn == null) {
            return Result.fail("服务函数不能为空！");
        }
        // 1. 从缓存拿Service信息：有就直接用，没有就解析并缓存
        LambdaMeta<T> lambdaMeta = (LambdaMeta<T>) CACHE_LAMBDA.computeIfAbsent(fn, k -> {
            // 解析Lambda，拿到Service实例、类名这些信息
            LambdaMeta<T> meta = parseSerialFunction(fn);
            log.debug("缓存Service信息：{}", meta.getServiceName());
            return meta;
        });
        // 2. 创建执行器，把Lambda、参数、Service信息传进去
        ServiceExecutor<T, U, R> executor = InstBuilder.of(ServiceExecutor.class)
                .set(ServiceExecutor::setServiceFn, fn)    // 传Lambda方法
                .set(ServiceExecutor::setParam, param)      // 传参数
                .set(ServiceExecutor::setLambdaMeta, lambdaMeta)  // 传Service信息
                .build();  // 构建执行器
        // 3. 执行方法，返回结果
        return executor.callService();
    }
    // 解析Lambda：从Lambda里拿到Service类名、实例、方法名
    @SuppressWarnings("unchecked")
    private static<T, U, R> LambdaMeta <T> parseSerialFunction(SerialBiFunction<T, U, R> fn) {
        // 用LambdaUtil拿到Lambda的元数据
        SerializedLambda lambda = LambdaUtil.valueOf(fn);
        // 封装Service信息的对象
        LambdaMeta<T> lambdaMeta = new LambdaMeta<>();
        // 1. 解析Service类名：Lambda里的类名是“com/example/UserService”，要改成“com.example.UserService”
        String tClassName = lambda.getImplClass().replaceAll("/", ".");
        try {
            // 2. 拿到Service的Class对象（比如UserService.class）
            Class<T> aClass = (Class<T>) Class.forName(tClassName);
            // 3. 从Spring里拿Service实例（不用@Autowired就是靠这行）
            T inst = SpringUtil.getBean(aClass);
            // 4. 把信息存到lambdaMeta里
            lambdaMeta.setClazz(aClass);    // 存Service的Class
            lambdaMeta.setInst(inst);      // 存Service实例
            lambdaMeta.setServiceName(lambda.getImplMethodName());  // 存方法名（比如queryUser）
        } catch (ClassNotFoundException e) {
            // 找不到类就抛异常
            throw new RuntimeException ("没找到Service类：" + tClassName, e);
        }
        return lambdaMeta;
    }

    // 封装Service信息的内部类：存Class、实例、方法名
    @lombok.Data
    public static class LambdaMeta<T> {
        private Class<T> clazz;          // Service的Class（比如UserService.class）
        private T inst;                  // Service实例（Spring里的Bean）
        private String serviceName;      // 方法名（比如queryUser）
    }
}

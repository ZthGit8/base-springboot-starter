package com.my.base.common.utils;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Optional;

public class SpElUtil {
    private static final ExpressionParser parser = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 解析请求参数
     * @param method
     * @param args
     * @param spEl
     * @return
     * @throws IllegalAccessException
     */
    public static String parseSpEl(Method method, Object[] args, String spEl) throws IllegalAccessException {
        String[] params = Optional.ofNullable(parameterNameDiscoverer.getParameterNames(method)).orElse(new String[]{});
        EvaluationContext context = new StandardEvaluationContext(); // 初始化默认上下文
        
        for (int i = 0; i < params.length; i++) {
            Object arg = args[i];
            if (arg != null) {  // 添加空值检查
                Class<?> aClass = arg.getClass();
                if (aClass.isPrimitive() || aClass.isAssignableFrom(Number.class)) {
                    context.setVariable(params[i], arg);
                } else {
                    context = new StandardEvaluationContext(arg);
                }
            }
        }
        
        Expression expression = parser.parseExpression(spEl);
        return expression.getValue(context, String.class);
    }

    public static String getMethodKey(Method method) {
        return method.getDeclaringClass() + "#" + method.getName();
    }
}

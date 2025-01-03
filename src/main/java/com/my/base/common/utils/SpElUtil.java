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
        String[] params = Optional.of(parameterNameDiscoverer.getParameterNames(method)).orElse(new String[]{});//解析参数名
        EvaluationContext context = null;//el解析需要的上下文对象
        for (int i = 0; i < params.length; i++) {
            Object arg = args[i];
            Class<?> aClass = arg.getClass();
            //判断 aClass 是否是基本数据类型或基本类型包装类
            if (aClass.isPrimitive() || aClass.isAssignableFrom(Number.class)) {
                context = new StandardEvaluationContext();
                context.setVariable(params[i], arg);//基本数据类型直接扔进去
            } else {//对象类型获取字段上的注解判断是否需要忽略
                context = new StandardEvaluationContext(arg);
            }
        }
        Expression expression = parser.parseExpression(spEl);
        return expression.getValue(context, String.class);
    }

    public static String getMethodKey(Method method) {
        return method.getDeclaringClass() + "#" + method.getName();
    }
}

package com.my.base.shared.util;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

/**
 * @author
 * @date 2025/12/2 11:13
 * @description: 从Lambda表达式里拿Service信息的工具
 */
public class LambdaUtil {
// 传个Lambda进来，返回它对应的“元数据”（比如哪个Service，哪个方法）
   public static SerializedLambda valueOf(Serializable lambda) {
       if (lambda == null) {
           throw new IllegalArgumentException("Lambda不能传空！");
        }
       try {
           // 反射拿到Lambda里的隐藏方法，不用管这行是咋回事
            Method writeReplaceMethod = lambda.getClass().getDeclaredMethod("writeReplace");
            writeReplaceMethod.setAccessible(true);
           return (SerializedLambda) writeReplaceMethod.invoke(lambda);
        } catch (Exception e) {
           throw new RuntimeException("解析Lambda出错了", e);
        }
    }
}

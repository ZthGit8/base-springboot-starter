package com.my.base.shared.util;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 编程式事务工具类
 */
public class TransactionUtil {

    private static volatile TransactionTemplate transactionTemplate;

    /**
     * 获取TransactionTemplate实例
     *
     * @return TransactionTemplate实例
     */
    private static TransactionTemplate getTransactionTemplate() {
        if (transactionTemplate == null) {
            synchronized (TransactionUtil.class) {
                if (transactionTemplate == null) {
                    PlatformTransactionManager transactionManager = SpringUtil.getBean(PlatformTransactionManager.class);
                    transactionTemplate = new TransactionTemplate(transactionManager);
                }
            }
        }
        return transactionTemplate;
    }

    /**
     * 执行带返回值的事务操作
     *
     * @param action 事务操作
     * @param <T>    返回值类型
     * @return 操作结果
     */
    public static <T> T execute(TransactionCallback<T> action) {
        return getTransactionTemplate().execute(action);
    }

    /**
     * 执行无返回值的事务操作
     *
     * @param action 事务操作
     */
    public static void executeWithoutResult(TransactionCallback<Void> action) {
        getTransactionTemplate().execute(action);
    }

    /**
     * 使用自定义事务属性执行带返回值的事务操作
     *
     * @param action              事务操作
     * @param propagationBehavior 传播行为
     * @param isolationLevel      隔离级别
     * @param timeout             超时时间
     * @param readOnly            是否只读
     * @param <T>                 返回值类型
     * @return 操作结果
     */
    public static <T> T execute(TransactionCallback<T> action,
                                int propagationBehavior,
                                int isolationLevel,
                                int timeout,
                                boolean readOnly) {
        PlatformTransactionManager transactionManager = SpringUtil.getBean(PlatformTransactionManager.class);
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(propagationBehavior);
        def.setIsolationLevel(isolationLevel);
        def.setTimeout(timeout);
        def.setReadOnly(readOnly);
        TransactionTemplate template = new TransactionTemplate(transactionManager, def);
        return template.execute(action);
    }

    /**
     * 手动控制事务回滚
     *
     * @param status 事务状态
     */
    public static void setRollbackOnly(TransactionStatus status) {
        status.setRollbackOnly();
    }

    /**
     * 带异常处理的事务执行方法
     *
     * @param action           事务操作
     * @param exceptionHandler 异常处理器
     * @param <T>              返回值类型
     * @return 操作结果
     */
    public static <T> T executeWithExceptionHandling(
            TransactionCallback<T> action,
            java.util.function.Function<Exception, T> exceptionHandler) {
        try {
            return getTransactionTemplate().execute(action);
        } catch (Exception e) {
            return exceptionHandler.apply(e);
        }
    }
}

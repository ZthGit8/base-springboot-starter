package com.my.base.common.interceptor;

import cn.hutool.core.date.StopWatch;
import cn.hutool.extra.spring.SpringUtil;
import com.my.base.common.storage.log.SlowSqlLogStorage;
import com.my.base.config.property.BaseProperties;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Properties;

@Component
@ConditionalOnProperty(name = "my.base.sql-log-enable", havingValue = "true")
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class,
                Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class,
                Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})})
public class SqlStatementInterceptor implements Interceptor {

    public static final Logger log = LoggerFactory.getLogger("sys-sql");

    private final BaseProperties baseProperties;

    public SqlStatementInterceptor(BaseProperties baseProperties) {
        this.baseProperties = baseProperties;
    }

    /**
     * 拦截方法调用并监控其执行时间
     * 如果执行时间超过1秒且小于5秒，则记录日志信息
     * 此外，还处理SQL异常并记录相关信息
     *
     * @param invocation 被拦截的调用对象
     * @return 执行结果
     * @throws Throwable 如果执行过程中发生异常
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 创建并启动计时器
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        boolean isSqlException = false;
        try {
            // 执行被拦截的方法
            return invocation.proceed();
        } catch (Throwable e) {
            // 检查异常原因是否为SQL异常
            if (e.getCause() != null && e.getCause() instanceof SQLException) {
                isSqlException = true;
            }
            // 重新抛出异常，以便调用者可以处理
            throw e;
        } finally {
            // 停止计时器
            stopWatch.stop();
            long timeConsuming = stopWatch.getTotalTimeMillis();
            // 如果执行时间大于1秒，则记录日志信息
            if (timeConsuming > 9) {
                log.info("执行SQL大于1s:{}ms", timeConsuming);
                // 获取执行的sql
                Object[] args = invocation.getArgs();
                BoundSql boundSql = (BoundSql) args[5];
                MappedStatement ms = (MappedStatement) args[0];
                // 判断是否是批量操作，如果是批量操作不记录日志
                if (ms.getId().contains("batch")) {
                    isSqlException = false;
                }
                // 打印日志
                String sql = MybatisPlusAllSqlLog.logInfoFromStatement(boundSql, ms, isSqlException);
                // 保存慢sql日志
                if (baseProperties.isSlowSqlLogEnable()) {
                    SlowSqlLogStorage slowSqlLogStorage = null;
                    try {
                        slowSqlLogStorage = SpringUtil.getBean(SlowSqlLogStorage.class);
                    } catch (Exception e) {
                        throw new IllegalStateException("开启了 slowSqlLogEnable 但是没有实现 SlowSqlLogStorage 的实现类");
                    }
                    if (slowSqlLogStorage != null) {
                        slowSqlLogStorage.save(sql, timeConsuming);
                    }
                }
            }
        }
    }


    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {

    }
}
package com.my.base.common.interceptor;

import cn.hutool.core.date.StopWatch;
import cn.hutool.extra.spring.SpringUtil;
import com.my.base.common.storage.log.SlowSqlLogStorage;
import com.my.base.config.properties.BaseProperties;
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
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class,
                Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class,
                Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})})
public class SqlStatementInterceptor implements Interceptor {

    public static final Logger log = LoggerFactory.getLogger("sys-sql");

    private BaseProperties baseProperties;

    @Autowired
    public SqlStatementInterceptor(BaseProperties baseProperties) {
        this.baseProperties = baseProperties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        try {
            return invocation.proceed();
        } finally {
            stopWatch.stop();
            long timeConsuming = stopWatch.getTotalTimeMillis();
            if (timeConsuming > 999 && timeConsuming < 5000) {
                log.info("执行SQL大于1s:{}ms", timeConsuming);
                // 获取执行的sql
                Object[] args = invocation.getArgs();
                BoundSql boundSql = (BoundSql) args[3];
                MappedStatement ms = (MappedStatement) args[0];
                // 打印日志
                String sql = MybatisPlusAllSqlLog.logInfoFromStatement(boundSql, ms);
                // 保存慢sql日志
                if (baseProperties.isSlowSqlLogEnable()) {
                    SlowSqlLogStorage slowSqlLogStorage = SpringUtil.getBean(SlowSqlLogStorage.class);
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
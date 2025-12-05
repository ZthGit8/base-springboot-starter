package com.my.base.persistence.mybatis;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.RowConstructor;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;

@Component
@Intercepts(
        {
                @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
        }
)
public class DataIsolationInterceptor implements Interceptor {
    /**
     * 从配置文件中环境变量
     */
    @Value("${spring.profiles.active}")
    private String env;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();
        //确保只有拦截的目标对象是 StatementHandler 类型时才执行特定逻辑
        if (target instanceof StatementHandler) {
            StatementHandler statementHandler = (StatementHandler) target;
            // 获取 BoundSql 对象，包含原始 SQL 语句
            BoundSql boundSql = statementHandler.getBoundSql();
            String originalSql = boundSql.getSql();
            String newSql = setEnvToStatement(originalSql);
            // 使用MetaObject对象将新的SQL语句设置到BoundSql对象中
            MetaObject metaObject = SystemMetaObject.forObject(boundSql);
            metaObject.setValue("sql", newSql);
        }
        // 执行SQL
        return invocation.proceed();
    }

    private String setEnvToStatement(String originalSql) {
        net.sf.jsqlparser.statement.Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(originalSql);
        } catch (JSQLParserException e) {
            throw new RuntimeException("EnvironmentVariableInterceptor::SQL语句解析异常：" + originalSql);
        }
        if (statement instanceof Select) {
            Select select = (Select) statement;
            PlainSelect selectBody = select.getPlainSelect();
            if (selectBody.getFromItem() instanceof Table) {
                Expression newWhereExpression;
                if (selectBody.getJoins() == null || selectBody.getJoins().isEmpty()) {
                    newWhereExpression = setEnvToWhereExpression(selectBody.getWhere(), null);
                } else {
                    // 如果是多表关联查询，在关联查询中新增每个表的环境变量条件
                    newWhereExpression = multipleTableJoinWhereExpression(selectBody);
                }
                // 将新的where设置到Select中
                selectBody.setWhere(newWhereExpression);
            } else if (selectBody.getFromItem() instanceof Select) {
                // 如果是子查询，在子查询中新增环境变量条件
                Select subSelect = (Select) selectBody.getFromItem();
                PlainSelect subSelectBody = subSelect.getPlainSelect();
                Expression newWhereExpression = setEnvToWhereExpression(subSelectBody.getWhere(), null);
                subSelectBody.setWhere(newWhereExpression);
            }

            // 获得修改后的语句
            return select.toString();
        } else if (statement instanceof Insert) {
            Insert insert = (Insert) statement;
            setEnvToInsert(insert);

            return insert.toString();
        } else if (statement instanceof Update) {
            Update update = (Update) statement;
            Expression newWhereExpression = setEnvToWhereExpression(update.getWhere(), null);
            // 将新的where设置到Update中
            update.setWhere(newWhereExpression);

            return update.toString();
        } else if (statement instanceof Delete) {
            Delete delete = (Delete) statement;
            Expression newWhereExpression = setEnvToWhereExpression(delete.getWhere(), null);
            // 将新的where设置到delete中
            delete.setWhere(newWhereExpression);

            return delete.toString();
        }
        return originalSql;
    }

    /**
     * 将需要隔离的字段加入到SQL的Where语法树中
     *
     * @param whereExpression SQL的Where语法树
     * @param alias           表别名
     * @return 新的SQL Where语法树
     */
    private Expression setEnvToWhereExpression(Expression whereExpression, String alias) {
        // 添加SQL语法树的一个where分支，并添加环境变量条件
        AndExpression andExpression = new AndExpression();
        EqualsTo envEquals = new EqualsTo();
        envEquals.setLeftExpression(new Column(StringUtils.isNotBlank(alias) ? String.format("%s.env", alias) : "env"));
        envEquals.setRightExpression(new StringValue(env));
        if (whereExpression == null) {
            return envEquals;
        } else {
            // 将新的where条件加入到原where条件的右分支树
            andExpression.setRightExpression(envEquals);
            andExpression.setLeftExpression(whereExpression);
            return andExpression;
        }
    }

    /**
     * 多表关联查询时，给关联的所有表加入环境隔离条件
     *
     * @param selectBody select语法树
     * @return 新的SQL Where语法树
     */
    private Expression multipleTableJoinWhereExpression(PlainSelect selectBody) {
        Table mainTable = selectBody.getFromItem(Table.class);
        String mainTableAlias = mainTable.getAlias().getName();
        // 将 t1.env = ENV 的条件添加到where中
        Expression newWhereExpression = setEnvToWhereExpression(selectBody.getWhere(), mainTableAlias);
        List<Join> joins = selectBody.getJoins();
        for (Join join : joins) {
            FromItem joinRightItem = join.getRightItem();
            if (joinRightItem instanceof Table) {
                Table joinTable = (Table) joinRightItem;
                String joinTableAlias = joinTable.getAlias().getName();
                // 将每一个join的 tx.env = ENV 的条件添加到where中
                newWhereExpression = setEnvToWhereExpression(newWhereExpression, joinTableAlias);
            }
        }
        return newWhereExpression;
    }

    /**
     * 新增数据时，插入env字段
     *
     * @param insert Insert 语法树
     */
    private void setEnvToInsert(Insert insert) {
        // 添加env列
        List<Column> columns = insert.getColumns();
        columns.add(new Column("env"));

        // 获取 VALUES 部分的 ExpressionList
        if (insert.getWithItemsList() instanceof ExpressionList) {
            List<WithItem> expressions = insert.getWithItemsList();

            // 遍历每一行的值，添加环境变量
            for (Expression expression : expressions) {
                if (expression instanceof RowConstructor) {
                    List<Expression> exprList = (List<Expression>) ((RowConstructor<?>) expression).getExpressions();
                    exprList.add(new StringValue(env));
                }
            }
        }
    }
}

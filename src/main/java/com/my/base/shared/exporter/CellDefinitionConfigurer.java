package com.my.base.shared.exporter;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 表格定义配置器
 *
 * @param <Entity>
 */
public class CellDefinitionConfigurer<Entity> {

    /**
     * 数据表格定义列表
     */
    private List<CellDefinition<Entity>> cellDefinitionList = new ArrayList<>();

    public List<CellDefinition<Entity>> getCellDefinitionList() {
        return cellDefinitionList;
    }

    public CellDefinitionConfigurer<Entity> add(String name, CellDefinition.CellValue<Entity> value) {
        if (StringUtils.isEmpty(name) || Objects.isNull(value)) {
            throw new IllegalArgumentException(String.format("illegal argument: name=%s, value=%s", name, value));
        }

        cellDefinitionList.add(new CellDefinition<>(name, value));
        return this;
    }

    /**
     * 添加表格宽度
     * @param name
     * @param value
     * @param columnWidth
     * @return
     */
    public CellDefinitionConfigurer<Entity> add(String name, CellDefinition.CellValue<Entity> value, long columnWidth) {
        if (StringUtils.isEmpty(name) || Objects.isNull(value)) {
            throw new IllegalArgumentException(String.format("illegal argument: name=%s, value=%s", name, value));
        }

        cellDefinitionList.add(new CellDefinition<>(name, value, (int) columnWidth));
        return this;
    }

    /**
     * 添加表格宽度
     * @param name
     * @param value
     * @param charNum
     * @return
     */
    public CellDefinitionConfigurer<Entity> add(String name, CellDefinition.CellValue<Entity> value, int charNum) {
        if (StringUtils.isEmpty(name) || Objects.isNull(value)) {
            throw new IllegalArgumentException(String.format("illegal argument: name=%s, value=%s", name, value));
        }

        cellDefinitionList.add(new CellDefinition<>(name, value, charNum * 256));
        return this;
    }

    /**
     * 添加表格宽度
     * @param name
     * @param value
     * @param charNum
     * @return
     */
    public CellDefinitionConfigurer<Entity> addMultiCellValue(String name, CellDefinition.MultiCellValue<Entity, Object> value, int charNum) {
        if (StringUtils.isEmpty(name) || Objects.isNull(value)) {
            throw new IllegalArgumentException(String.format("illegal argument: name=%s, value=%s", name, value));
        }

        cellDefinitionList.add(new CellDefinition<>(name, value, charNum * 256));
        return this;
    }

    public CellDefinitionConfigurer<Entity> add(String name, CellDefinition.CellValue<Entity> value, boolean isAdd) {
        return isAdd ? add(name, value) : this;
    }
}
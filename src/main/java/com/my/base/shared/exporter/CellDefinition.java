package com.my.base.shared.exporter;

import java.util.function.Function;

/**
 * 表格定义vo
 *
 * @param <Entity>
 */
public class CellDefinition<Entity> {

    /**
     * 表格格子名称
     */
    private String name;

    /**
     * 表格数据提供者
     */
    private CellValue<Entity> value;

    /**
     * 表格数据提供者
     */
    private MultiCellValue<Entity,Object> multiCellValue;

    /**
     * 列宽
     */
    private int columnWidth;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CellValue<Entity> getValue() {
        return value;
    }

    public void setValue(CellValue<Entity> value) {
        this.value = value;
    }

    public MultiCellValue<Entity, Object> getMultiCellValue() {
        return multiCellValue;
    }

    public void setMultiCellValue(MultiCellValue<Entity, Object> multiCellValue) {
        this.multiCellValue = multiCellValue;
    }

    public int getColumnWidth() {
        return columnWidth;
    }

    public void setColumnWidth(int columnWidth) {
        this.columnWidth = columnWidth;
    }

    public CellDefinition(String name, CellValue<Entity> value) {
        this.name = name;
        this.value = value;
    }

    public CellDefinition(String name, MultiCellValue<Entity,Object> multiCellValue, int columnWidth) {
        this.name = name;
        this.multiCellValue = multiCellValue;
        this.columnWidth = columnWidth;
    }

    public CellDefinition(String name, CellValue<Entity> value, int columnWidth) {
        this.name = name;
        this.value = value;
        this.columnWidth = columnWidth;
    }

    public interface CellValue<Entity> extends Function<Entity, Object> {

    }

    @FunctionalInterface
    public interface MultiCellValue<ReportEntity,R> {
        R apply(ReportEntity t);

    }
}
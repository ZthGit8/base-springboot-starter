package com.my.base.shared.exporter.domain;

/**
 * @author
 * @date 2025/7/10 17:24
 * @description: 存放单元格信息
 */
public class CellInfo {
    /**
     * 列名
     */
    private String name;
    /**
     * 列值
     */
    private Object value;
    /**
     * 列宽
     */
    private int columnWidth = 10 * 256;

    public CellInfo() {
    }

    public CellInfo(String name, Object value, int columnWidth) {
        this.name = name;
        this.value = value;
        this.columnWidth = columnWidth;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public int getColumnWidth() {
        return columnWidth;
    }

    public void setColumnWidth(int columnWidth) {
        this.columnWidth = columnWidth;
    }
}

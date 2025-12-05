package com.my.base.shared.exporter;

import java.util.List;

public class SheetData<Entity> {
    /**
     * 数据 Entity
     */
    private List<Entity> dataList;
    /**
     * 单元格定义
     */
    private CellDefinitionConfigurer<Entity> cellDefinitionConfigurer;

    public SheetData(List<Entity> dataList,CellDefinitionConfigurer<Entity> cellDefinitionConfigurer) {
        this.dataList = dataList;
        this.cellDefinitionConfigurer = cellDefinitionConfigurer;
    }

    public List<Entity> getDataList() {
        return dataList;
    }

    public void setDataList(List<Entity> dataList) {
        this.dataList = dataList;
    }

    public CellDefinitionConfigurer<Entity> getCellDefinitionConfigurer() {
        return cellDefinitionConfigurer;
    }

    public void setCellDefinitionConfigurer(CellDefinitionConfigurer<Entity> cellDefinitionConfigurer) {
        this.cellDefinitionConfigurer = cellDefinitionConfigurer;
    }
}
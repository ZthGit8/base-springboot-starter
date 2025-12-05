package com.my.base.shared.exporter;

import cn.hutool.core.date.DateUtil;
import com.my.base.shared.exporter.domain.CellInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据导出服务
 */
@Component
public class ExcelMultiSheetExporter {

    private static final Logger logger = LoggerFactory.getLogger(ExcelMultiSheetExporter.class);

    /**
     * 获取Base64编码的Excel文件
     *
     * @return
     */
    public static <T extends ReportEntity> String exportBase64Str(String fileName, Map<String, SheetData<T>> map) {

        // 校验配置是否都存在
        if (map.values().stream().anyMatch(sheetData -> sheetData.getCellDefinitionConfigurer() == null)) {
            logger.warn("导出excel配置为空");
            return "";
        }
        // 表格数据
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            exportExcelMultiSheet(fileName, map, out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            logger.error("导出excel出错：{}", e.getMessage(), e);
        }
        return "";
    }

    /**
     * 导出Excel文档
     *
     * @param fileName 文件名
     * @param map      key为 sheetName，value 为 List<SheetData<Entity>>
     * @param response HTTP响应对象
     */
    public static <T extends ReportEntity> void exportOutPutStream(String fileName, Map<String, SheetData<T>> map, HttpServletResponse response) {
        // 校验配置是否都存在
        if (map.values().stream().anyMatch(sheetData -> sheetData.getCellDefinitionConfigurer() == null)) {
            logger.warn("导出excel配置为空");
            return;
        }
        // 表格数据
        try {
            // 设置响应编码和内容类型
            response.setCharacterEncoding("utf-8");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
            // 设置响应头，允许跨域访问
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
            // 设置文件下载响应头
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName + ".xlsx");
            // 获取响应输出流
            OutputStream outs = response.getOutputStream();
            // 直接写入到响应流
            exportExcelMultiSheet(fileName, map, outs);
            // 刷新输出流
            outs.flush();
        } catch (Exception e) {
            // 记录导出错误日志
            logger.error("导出excel出错：{}", e.getMessage(), e);
        }
    }


    /**
     * map key为 sheetName，value 为 List<SheetData<Entity>>
     * 导出Excel表格的私有静态方法（使用SXSSFWorkbook流式输出）
     * 该方法泛型化，可以处理不同类型的数据实体
     */
    private static <T extends ReportEntity> void exportExcelMultiSheet(
            String fileName,
            Map<String, SheetData<T>> map,
            OutputStream outputStream) throws Exception {
        // 创建SXSSFWorkbook，内存中保留10000行，超过的会写入磁盘
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(10000)) {
            for (Map.Entry<String, SheetData<T>> entry : map.entrySet()) {
                String sheetName = entry.getKey();
                SheetData<T> sheetData = entry.getValue();
                List<T> dataList = sheetData.getDataList();
                List<CellDefinition<T>> cellDefinitionList = sheetData.getCellDefinitionConfigurer().getCellDefinitionList();

                // 表格列名称
                List<String> cellNameList = cellDefinitionList.stream().map(CellDefinition::getName).collect(Collectors.toList());

                // 如果所有列宽度都设置了，则使用CellInfo方式创建表格
                if (cellDefinitionList.stream().allMatch(c -> c.getColumnWidth() > 0)) {
                    List<List<CellInfo>> cellInfoList = dataList.stream().map(data -> {
                        List<CellInfo> cellInfos = new ArrayList<>();
                        for (CellDefinition<T> cellDefinition : cellDefinitionList) {
                            cellInfos.add(new CellInfo(cellDefinition.getName(), cellDefinition.getMultiCellValue().apply(data), cellDefinition.getColumnWidth()));
                        }
                        return cellInfos;
                    }).toList();
                    createWorkbookByCellInfo(workbook, sheetName, cellNameList, cellInfoList);

                } else {
                    // 将数据列表转换为适合导出的格式
                    List<Map<String, Object>> exportDataList = dataList.stream()
                            .map(data -> {
                                Map<String, Object> rowMap = new HashMap<>();
                                for (CellDefinition<T> cellDefinition : cellDefinitionList) {
                                    // 根据单元格定义，将数据实体中的数据提取到Map中
                                    rowMap.put(cellDefinition.getName(), cellDefinition.getMultiCellValue().apply(data));
                                }
                                return rowMap;
                            })
                            .collect(Collectors.toList());

                    // 创建Workbook对象，用于生成Excel表格
                    createWorkbook(workbook, sheetName, cellNameList, exportDataList);
                }
            }
            // 直接写入输出流
            workbook.write(outputStream);
        }
    }

    /**
     * 创建一个包含指定数据的Excel工作簿
     *
     * @param workbook  工作簿对象
     * @param sheetName 工作表的名称，需要进行URL解码
     * @param titleList 表头标题的列表
     * @param dataList  数据列表，每个元素是一个映射，键是标题，值是单元格内容
     */
    private static void createWorkbook(Workbook workbook, String sheetName, List<String> titleList, List<Map<String, Object>> dataList) {
        // 对sheetName进行URL解码
        try {
            // 检查是否需要URL解码：如果字符串包含%且解码后与原字符串不同
            String decoded = URLDecoder.decode(sheetName, StandardCharsets.UTF_8);
            if (!sheetName.equals(decoded)) {
                sheetName = decoded;
            }
        } catch (Exception e) {
            // 如果解码失败，保持原始sheetName
            logger.debug("Sheet name解码失败，使用原始名称: {}", sheetName);
        }
        // 创建一个工作表
        Sheet sheet = workbook.createSheet(sheetName);
        // 初始化行号
        int rowNum = 0;
        // 创建第一行
        Row row = sheet.createRow(rowNum);
        // 创建单元格样式
        CellStyle style = workbook.createCellStyle();
        // 设置对齐方式
        style.setAlignment(HorizontalAlignment.CENTER_SELECTION);
        style.setVerticalAlignment(VerticalAlignment.BOTTOM);
        // 设置表头
        int column = 0;
        for (String title : titleList) {
            row.createCell(column).setCellValue(title);
            column++;
        }

        // 遍历数据列表，填充数据到工作表
        for (Map<String, Object> data : dataList) {
            rowNum++;
            row = sheet.createRow(rowNum);
            column = 0;
            for (String title : titleList) {
                Cell cell = row.createCell(column++);
                cell.setCellStyle(style);
                Object value = data.get(title);
                // 根据数据类型设置单元格内容
                dataConvert(value, cell);
            }
        }
        // 自动调整列宽
        for (int i = 0; i < titleList.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }


    /**
     * 创建一个包含指定数据的Excel工作簿
     *
     * @param workbook     工作簿对象
     * @param sheetName    工作表的名称，需要进行URL解码
     * @param titleList    表头标题的列表
     * @param cellInfoList 数据列表，每个元素是一个映射，键是标题，值是单元格内容
     */
    private static void createWorkbookByCellInfo(Workbook workbook, String sheetName, List<String> titleList, List<List<CellInfo>> cellInfoList) {
        // 对sheetName进行URL解码
        try {
            // 检查是否需要URL解码：如果字符串包含%且解码后与原字符串不同
            String decoded = URLDecoder.decode(sheetName, StandardCharsets.UTF_8);
            if (!sheetName.equals(decoded)) {
                sheetName = decoded;
            }
        } catch (Exception e) {
            // 如果解码失败，保持原始sheetName
            logger.debug("Sheet name解码失败，使用原始名称: {}", sheetName);
        }
        // 创建一个工作表
        Sheet sheet = workbook.createSheet(sheetName);
        // 初始化行号
        int rowNum = 0;
        // 创建第一行
        Row row = sheet.createRow(rowNum);
        // 创建单元格样式
        CellStyle style = workbook.createCellStyle();
        // 设置对齐方式
        style.setAlignment(HorizontalAlignment.CENTER_SELECTION);
        style.setVerticalAlignment(VerticalAlignment.BOTTOM);
        // 设置表头
        int column = 0;
        for (String title : titleList) {
            Cell cell = row.createCell(column);
            cell.setCellStyle(style);
            cell.setCellValue(title);
            column++;
        }

        // 遍历数据列表，填充数据到工作表
        for (List<CellInfo> cellInfos : cellInfoList) {
            rowNum++;
            row = sheet.createRow(rowNum);
            column = 0;
            for (int j = 0; j < titleList.size(); j++) {
                String title = titleList.get(j);
                Cell cell = row.createCell(column++);
                cell.setCellStyle(style);
                CellInfo currentCellInfo = cellInfos.stream().filter(c -> c.getName().equals(title)).findFirst().orElse(new CellInfo());
                Object value = currentCellInfo.getValue();
                int columnWidth = currentCellInfo.getColumnWidth();
                sheet.setColumnWidth(j, columnWidth);
                // 根据数据类型设置单元格内容
                dataConvert(value, cell);
            }

        }
    }

    private static void dataConvert(Object value, Cell cell) {
        if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Integer || value instanceof Long) {
            cell.setCellValue(((Number) value).longValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof Date) {
            cell.setCellValue(DateUtil.formatDateTime((Date) value));
        } else if (value == null) {
            cell.setCellValue("");
        } else {
            cell.setCellValue(value.toString());
        }
    }


}

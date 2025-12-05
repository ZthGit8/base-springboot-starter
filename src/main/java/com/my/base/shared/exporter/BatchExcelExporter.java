package com.my.base.shared.exporter;

import cn.hutool.core.date.DateUtil;
import com.my.base.shared.exporter.domain.CellInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 批量数据导出服务（支持多线程查询 + 单线程写入）
 * 适用于百万级数据导出，避免内存溢出
 *
 * @author
 * @date 2025/12/5
 */
@Component
public class BatchExcelExporter {

    private static final Logger logger = LoggerFactory.getLogger(BatchExcelExporter.class);

    /**
     * 默认每批次查询数量
     */
    private static final int DEFAULT_BATCH_SIZE = 10000;

    /**
     * 默认查询线程数
     */
    private static final int DEFAULT_QUERY_THREADS = 4;

    /**
     * 阻塞队列容量（避免内存积压）
     */
    private static final int QUEUE_CAPACITY = 10;

    /**
     * 批量导出Excel（多线程查询 + 单线程写入）
     *
     * @param fileName        文件名
     * @param sheetName       工作表名
     * @param totalCount      总数据量
     * @param batchQueryFunc  批次查询函数（参数：起始ID/偏移量、批次大小）
     * @param cellDefinitionConfigurer 单元格定义配置器
     * @param response        HTTP响应对象
     * @param <Entity>        泛型实体类
     */
    public static <Entity> void batchExport(
            String fileName,
            String sheetName,
            long totalCount,
            BatchQueryFunction<Entity> batchQueryFunc,
            CellDefinitionConfigurer<Entity> cellDefinitionConfigurer,
            HttpServletResponse response) {
        batchExport(fileName, sheetName, totalCount, batchQueryFunc, cellDefinitionConfigurer, response,
                DEFAULT_BATCH_SIZE, DEFAULT_QUERY_THREADS);
    }

    /**
     * 批量导出Excel（多线程查询 + 单线程写入）
     *
     * @param fileName        文件名
     * @param sheetName       工作表名
     * @param totalCount      总数据量
     * @param batchQueryFunc  批次查询函数（参数：批次号、批次大小）
     * @param cellDefinitionConfigurer 单元格定义配置器
     * @param response        HTTP响应对象
     * @param batchSize       每批次查询数量
     * @param queryThreads    查询线程数
     * @param <Entity>        泛型实体类
     */
    public static <Entity> void batchExport(
            String fileName,
            String sheetName,
            long totalCount,
            BatchQueryFunction<Entity> batchQueryFunc,
            CellDefinitionConfigurer<Entity> cellDefinitionConfigurer,
            HttpServletResponse response,
            int batchSize,
            int queryThreads) {

        if (sheetName == null || sheetName.isEmpty()) {
            sheetName = fileName;
        }

        // 获取列定义
        List<CellDefinition<Entity>> cellDefinitionList = cellDefinitionConfigurer.getCellDefinitionList();
        if (cellDefinitionList == null || cellDefinitionList.isEmpty()) {
            logger.warn("导出excel配置为空");
            return;
        }

        try {
            // 设置响应头
            response.setCharacterEncoding("utf-8");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName + ".xlsx");

            // 获取响应输出流
            OutputStream outputStream = response.getOutputStream();

            // 执行批量导出
            batchExportToStream(sheetName, totalCount, batchQueryFunc, cellDefinitionList,
                    outputStream, batchSize, queryThreads);

            outputStream.flush();
            logger.info("批量导出Excel完成，文件名：{}，总数据量：{}", fileName, totalCount);

        } catch (Exception e) {
            logger.error("批量导出excel出错：{}", e.getMessage(), e);
        }
    }

    /**
     * 批量导出到输出流
     */
    private static <Entity> void batchExportToStream(
            String sheetName,
            long totalCount,
            BatchQueryFunction<Entity> batchQueryFunc,
            List<CellDefinition<Entity>> cellDefinitionList,
            OutputStream outputStream,
            int batchSize,
            int queryThreads) throws Exception {

        // 计算总批次数
        int totalBatches = (int) Math.ceil((double) totalCount / batchSize);
        logger.info("开始批量导出，总数据量：{}，批次大小：{}，总批次数：{}，查询线程数：{}",
                totalCount, batchSize, totalBatches, queryThreads);

        // 创建有序阻塞队列（使用LinkedBlockingQueue保证FIFO）
        BlockingQueue<BatchData<Entity>> dataQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

        // 异常标记
        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicInteger completedBatches = new AtomicInteger(0);

        // 创建线程池
        try (ExecutorService queryExecutor = new ThreadPoolExecutor(
                queryThreads,
                queryThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(totalBatches),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "batch-query-" + threadNumber.getAndIncrement());
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        )) {

            // 启动写入线程
            CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
                try {
                    writeToExcel(sheetName, cellDefinitionList, dataQueue, totalBatches, outputStream);
                } catch (Exception e) {
                    hasError.set(true);
                    logger.error("写入Excel失败", e);
                }
            });

            // 提交查询任务
            try {
                for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
                    final int currentBatch = batchNum;
                    final long offset = (long) batchNum * batchSize;
                    final int currentBatchSize = (int) Math.min(batchSize, totalCount - offset);

                    queryExecutor.submit(() -> {
                        if (hasError.get()) {
                            return;
                        }

                        try {
                            logger.debug("开始查询第 {} 批数据，offset：{}，size：{}", currentBatch + 1, offset, currentBatchSize);
                            List<Entity> batchData = batchQueryFunc.query(offset, currentBatchSize);

                            if (batchData == null) {
                                batchData = Collections.emptyList();
                            }

                            // 将数据放入队列（阻塞直到有空间）
                            dataQueue.put(new BatchData<>(currentBatch, batchData));
                            logger.debug("第 {} 批数据已放入队列，数据量：{}", currentBatch + 1, batchData.size());

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            hasError.set(true);
                            logger.error("查询第 {} 批数据被中断", currentBatch + 1, e);
                        } catch (Exception e) {
                            hasError.set(true);
                            logger.error("查询第 {} 批数据失败", currentBatch + 1, e);
                        } finally {
                            completedBatches.incrementAndGet();
                        }
                    });
                }

                // 等待所有查询任务完成
                queryExecutor.shutdown();
                if (!queryExecutor.awaitTermination(1, TimeUnit.HOURS)) {
                    logger.warn("查询任务超时，强制关闭");
                    queryExecutor.shutdownNow();
                }

                // 等待写入完成
                writeFuture.get(1, TimeUnit.HOURS);

            } catch (Exception e) {
                logger.error("批量导出过程中发生异常", e);
                hasError.set(true);
                queryExecutor.shutdownNow();
                throw e;
            }
        }

        if (hasError.get()) {
            throw new RuntimeException("批量导出过程中发生错误");
        }
    }

    /**
     * 写入Excel（单线程按顺序写入）
     */
    private static <Entity> void writeToExcel(
            String sheetName,
            List<CellDefinition<Entity>> cellDefinitionList,
            BlockingQueue<BatchData<Entity>> dataQueue,
            int totalBatches,
            OutputStream outputStream) throws Exception {

        // 创建SXSSFWorkbook，内存中保留10000行
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(10000)) {
            // 对sheetName进行URL解码
            sheetName = URLDecoder.decode(sheetName, StandardCharsets.UTF_8);
            Sheet sheet = workbook.createSheet(sheetName);

            // 创建表头
            List<String> titleList = cellDefinitionList.stream()
                    .map(CellDefinition::getName)
                    .collect(Collectors.toList());

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            createHeader(sheet, titleList, headerStyle);

            // 当前行号
            int currentRow = 1;

            // 已处理的批次
            Map<Integer, List<Entity>> pendingBatches = new ConcurrentHashMap<>();
            int nextBatchToWrite = 0;

            // 按顺序写入数据
            while (nextBatchToWrite < totalBatches) {
                try {
                    // 从队列中取数据（超时时间10秒）
                    BatchData<Entity> batchData = dataQueue.poll(10, TimeUnit.SECONDS);

                    if (batchData == null) {
                        // 超时，继续等待
                        logger.debug("等待第 {} 批数据...", nextBatchToWrite + 1);
                        continue;
                    }

                    int batchNum = batchData.getBatchNum();
                    List<Entity> dataList = batchData.getData();

                    if (batchNum == nextBatchToWrite) {
                        // 正好是下一批，直接写入
                        currentRow = writeDataRows(sheet, dataList, cellDefinitionList, titleList,
                                dataStyle, currentRow);
                        nextBatchToWrite++;
                        logger.info("已写入第 {} 批数据，当前行号：{}", batchNum + 1, currentRow);

                        // 检查是否有待写入的后续批次
                        while (pendingBatches.containsKey(nextBatchToWrite)) {
                            List<Entity> pendingData = pendingBatches.remove(nextBatchToWrite);
                            currentRow = writeDataRows(sheet, pendingData, cellDefinitionList, titleList,
                                    dataStyle, currentRow);
                            nextBatchToWrite++;
                            logger.info("已写入待处理的第 {} 批数据，当前行号：{}", nextBatchToWrite, currentRow);
                        }
                    } else {
                        // 不是下一批，暂存
                        pendingBatches.put(batchNum, dataList);
                        logger.debug("第 {} 批数据暂存，等待前序批次", batchNum + 1);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("写入线程被中断", e);
                }
            }

            // 自动调整列宽
            for (int i = 0; i < titleList.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            // 写入输出流
            workbook.write(outputStream);
            logger.info("Excel写入完成，总行数：{}", currentRow);
        }
    }

    /**
     * 创建表头
     */
    private static void createHeader(Sheet sheet, List<String> titleList, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titleList.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titleList.get(i));
            cell.setCellStyle(style);
        }
    }

    /**
     * 写入数据行
     */
    private static <Entity> int writeDataRows(
            Sheet sheet,
            List<Entity> dataList,
            List<CellDefinition<Entity>> cellDefinitionList,
            List<String> titleList,
            CellStyle dataStyle,
            int startRow) {

        int currentRow = startRow;
        for (Entity data : dataList) {
            Row row = sheet.createRow(currentRow++);
            for (int i = 0; i < titleList.size(); i++) {
                Cell cell = row.createCell(i);
                cell.setCellStyle(dataStyle);

                CellDefinition<Entity> cellDefinition = cellDefinitionList.get(i);
                Object value = cellDefinition.getValue() != null
                        ? cellDefinition.getValue().apply(data)
                        : cellDefinition.getMultiCellValue().apply(data);

                dataConvert(value, cell);
            }
        }
        return currentRow;
    }

    /**
     * 创建表头样式
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);

        return style;
    }

    /**
     * 创建数据样式
     */
    private static CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * 数据类型转换
     */
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

    /**
     * 批次数据包装类
     */
    @Getter
    private static class BatchData<T> {
        private final int batchNum;
        private final List<T> data;

        public BatchData(int batchNum, List<T> data) {
            this.batchNum = batchNum;
            this.data = data;
        }

    }

    /**
     * 批次查询函数接口
     */
    @FunctionalInterface
    public interface BatchQueryFunction<T> {
        /**
         * 批次查询
         *
         * @param offset 偏移量（起始位置）
         * @param limit  查询数量
         * @return 查询结果列表
         */
        List<T> query(long offset, int limit);
    }
}

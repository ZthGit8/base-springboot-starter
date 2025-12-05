package com.my.base.shared.processor;

import lombok.Getter;

import com.my.base.shared.util.CompletableFutureUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 基于 CompletableFuture 的批量任务处理器：支持任务分片、并发执行、阶段协同、进度监控、结果汇总
 */
public class CompletableFutureBatchTaskProcessor<T> {
    // 线程池（核心配置）
    private final ExecutorService executorService;
    // 任务分片大小
    private final int batchSize;
    // 已完成任务数（原子类，线程安全）
    private final AtomicInteger completedCount = new AtomicInteger(0);
    // 失败任务数（原子类，线程安全）
    private final AtomicInteger failedCount = new AtomicInteger(0);
    // 任务执行结果列表（线程安全集合）
    private final List<TaskResult<T>> resultList = new CopyOnWriteArrayList<>();

    /**
     * 构造器：自定义线程池配置
     *
     * @param corePoolSize  核心线程数
     * @param maxPoolSize   最大线程数
     * @param keepAliveTime 非核心线程空闲时间（秒）
     * @param batchSize     任务分片大小
     */
    public CompletableFutureBatchTaskProcessor(int corePoolSize, int maxPoolSize, long keepAliveTime, int batchSize) {
        this.batchSize = Math.max(batchSize, 1); // 分片大小至少为1
        // 初始化线程池（自定义配置，避免OOM）
        this.executorService = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100), // 有界任务队列，避免任务堆积
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：提交线程自行执行
        );
    }

    /**
     * 核心方法：执行批量任务（使用 CompletableFuture 并发执行）
     *
     * @param taskList    原始任务列表
     * @param taskHandler 任务处理器（自定义任务执行逻辑）
     * @return 执行结果汇总
     * @throws InterruptedException 线程中断异常
     */
    public BatchResult<T> executeBatchTasks(List<T> taskList, TaskHandler<T> taskHandler) throws InterruptedException {
        if (taskList == null || taskList.isEmpty()) {
            return new BatchResult<>(0, 0, resultList);
        }

        // 1. 任务分片
        List<List<T>> taskShards = splitTask(taskList, batchSize);
        int totalTaskCount = taskList.size();

        System.out.println("📋 批量任务开始执行：");
        System.out.println(" - 总任务数：" + totalTaskCount);
        System.out.println(" - 任务分片数：" + taskShards.size());
        System.out.println(" - 线程池配置：" + executorService.toString());

        // 2. 为每个分片提交一个异步任务
        // 使用工具类并行执行每个分片任务并等待
        List<Void> shardResults = CompletableFutureUtil.parallelFutureJoin(
                taskShards,
                shard -> {
                    for (T task : shard) {
                        TaskResult<T> result = new TaskResult<>();
                        result.setTask(task);
                        try {
                            boolean success = taskHandler.process(task);
                            result.setSuccess(success);
                            if (success) {
                                completedCount.incrementAndGet();
                            } else {
                                failedCount.incrementAndGet();
                                result.setErrorMessage("任务执行失败（无异常）");
                            }
                        } catch (Exception e) {
                            failedCount.incrementAndGet();
                            result.setSuccess(false);
                            result.setErrorMessage("任务执行失败：" + e.getMessage());
                        }
                        // 存储任务结果（线程安全）
                        resultList.add(result);
                    }
                    return null;
                },
                (ex, shard) -> {
                    System.err.println("分片执行异常：" + ex.getMessage());
                    return null;
                }
        );
        // 4. 关闭线程池（不再接收新任务，等待已提交任务完成）
        executorService.shutdown();
        // 5. 返回汇总结果
        return new BatchResult<>(completedCount.get(), failedCount.get(), resultList);
    }

    /**
     * 进阶方法：分阶段批量任务（读取 → 处理 → 汇总），用 allOf 作为阶段屏障
     *
     * @param taskList       原始任务列表
     * @param readHandler    读取阶段处理器
     * @param processHandler 处理阶段处理器
     * @param summaryHandler 汇总阶段处理器
     * @return 最终汇总结果
     * @throws InterruptedException 线程中断异常
     */
    public String executePhasedBatchTasks(List<T> taskList,
                                          TaskHandler<T> readHandler,
                                          TaskHandler<T> processHandler,
                                          SummaryHandler<T> summaryHandler) throws InterruptedException {
        if (taskList == null || taskList.isEmpty()) {
            return "无任务可执行";
        }

        // 任务分片
        List<List<T>> taskShards = splitTask(taskList, batchSize);
        int shardCount = taskShards.size();

        System.out.println("📋 分阶段批量任务开始执行：");
        System.out.println(" - 总任务数：" + taskList.size());
        System.out.println(" - 分片数：" + shardCount);

        // 阶段一：读取（并行执行，每分片）
        CompletableFutureUtil.parallelFutureJoin(
                taskShards,
                shard -> {
                    for (T task : shard) {
                        try {
                            readHandler.process(task);
                        } catch (Exception e) {
                            System.err.println("读取阶段异常：" + e.getMessage());
                        }
                    }
                    return null;
                },
                (ex, shard) -> {
                    System.err.println("读取阶段分片异常：" + ex.getMessage());
                    return null;
                }
        );

        // 阶段二：处理（并行执行，每分片）
        CompletableFutureUtil.parallelFutureJoin(
                taskShards,
                shard -> {
                    for (T task : shard) {
                        try {
                            processHandler.process(task);
                        } catch (Exception e) {
                            System.err.println("处理阶段异常：" + e.getMessage());
                        }
                    }
                    return null;
                },
                (ex, shard) -> {
                    System.err.println("处理阶段分片异常：" + ex.getMessage());
                    return null;
                }
        );

        // 阶段三：汇总（并行执行，每分片）
        CompletableFutureUtil.parallelFutureJoin(
                taskShards,
                shard -> {
                    try {
                        summaryHandler.summary(shard);
                    } catch (Exception e) {
                        System.err.println("汇总阶段异常：" + e.getMessage());
                    }
                    return null;
                },
                (ex, shard) -> {
                    System.err.println("汇总阶段分片异常：" + ex.getMessage());
                    return null;
                }
        );

        executorService.shutdown();
        // 最终汇总结果
        return summaryHandler.getFinalSummary();
    }

    /**
     * 进阶方法重载：分阶段批量任务（读取→处理→汇总），阶段间通过泛型结果协同（CompletableFuture实现）
     *
     * @param taskList   原始任务列表
     * @param reader     读取阶段：T -> R
     * @param processor  处理阶段：R -> P
     * @param summarizer 汇总阶段：累加所有分片的 P，并返回最终结果 S
     * @param <R>        读取阶段结果类型
     * @param <P>        处理阶段结果类型
     * @param <S>        最终汇总结果类型
     * @return 最终汇总结果 S
     * @throws InterruptedException 线程中断异常
     */
    public <R, P, S> S executePhasedBatchTasks(List<T> taskList,
                                               Reader<T, R> reader,
                                               Processor<R, P> processor,
                                               Summarizer<P, S> summarizer,
                                               boolean parallel) {
        if (taskList == null || taskList.isEmpty()) {
            return summarizer.finish();
        }

        // 任务分片（每个分片对应一组 T）
        List<List<T>> taskShards = splitTask(taskList, batchSize);
        int shardCount = taskShards.size();

        System.out.println("📋 分阶段批量任务（管道式 CompletableFuture）开始执行：");
        System.out.println(" - 总任务数：" + taskList.size());
        System.out.println(" - 分片数：" + shardCount);

        // 阶段一：读取（为每个分片产生 List<R>）
        List<CompletableFuture<List<R>>> readCFs = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            final int shardIndex = i;
            List<T> shard = taskShards.get(i);
            CompletableFuture<List<R>> cf = CompletableFutureUtil.createFuture(
                    () -> {
                        System.out.printf("📥 分片%d开始读取阶段\n", shardIndex);
                        List<R> rr = new ArrayList<>();
                        for (T task : shard) {
                            try {
                                rr.add(reader.read(task));
                            } catch (Exception e) {
                                System.err.printf("❌ 分片%d读取阶段单任务异常：%s\n", shardIndex, e.getMessage());
                            }
                        }
                        System.out.printf("📥 分片%d读取阶段完成\n", shardIndex);
                        return rr;
                    },
                    ex -> {
                        System.err.printf("❌ 分片%d读取阶段异常：%s\n", shardIndex, ex.getMessage());
                        return List.of();
                    }
            );
            readCFs.add(cf);
        }
        List<List<R>> readResultsPerShard = CompletableFutureUtil.sequence(readCFs).join();

        // 阶段二：处理（消费各分片的 List<R> 生成 List<P>）
        List<CompletableFuture<List<P>>> processCFs = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            final int shardIndex = i;
            List<R> rr = readResultsPerShard.get(i);
            CompletableFuture<List<P>> cf = CompletableFutureUtil.createFuture(
                    () -> {
                        System.out.printf("⚙️  分片%d开始处理阶段\n", shardIndex);
                        List<P> pp = new ArrayList<>();
                        for (R r : rr) {
                            try {
                                pp.add(processor.process(r));
                            } catch (Exception e) {
                                System.err.printf("❌ 分片%d处理阶段单任务异常：%s\n", shardIndex, e.getMessage());
                            }
                        }
                        System.out.printf("⚙️  分片%d处理阶段完成\n", shardIndex);
                        return pp;
                    },
                    ex -> {
                        System.err.printf("❌ 分片%d处理阶段异常：%s\n", shardIndex, ex.getMessage());
                        return List.of();
                    }
            );
            processCFs.add(cf);
        }
        List<List<P>> processResultsPerShard = CompletableFutureUtil.sequence(processCFs).join();

        // 阶段三：汇总（对每个分片的 List<P> 进行累加）
        CompletableFutureUtil.parallelFutureJoin(
                processResultsPerShard,
                shardP -> {
                    int shardIndex = processResultsPerShard.indexOf(shardP);
                    System.out.printf("📊 分片%d开始汇总阶段\n", shardIndex);
                    try {
                        summarizer.accumulate(shardP);
                    } catch (Exception e) {
                        System.err.printf("❌ 分片%d汇总阶段异常：%s\n", shardIndex, e.getMessage());
                    }
                    System.out.printf("📊 分片%d汇总阶段完成\n", shardIndex);
                    return null;
                },
                (ex, shardP) -> {
                    System.err.println("汇总阶段分片异常：" + ex.getMessage());
                    return null;
                }
        );

        executorService.shutdown();
        // 返回最终汇总结果
        return summarizer.finish();
    }

    /**
     * 进阶方法重载：分阶段批量任务（读取→处理→汇总），阶段间通过泛型结果协同（CompletableFuture实现）
     *
     * @param taskList   原始任务列表
     * @param reader     读取阶段：T -> R
     * @param processor  处理阶段：R -> P
     * @param summarizer 汇总阶段：累加所有分片的 P，并返回最终结果 S
     * @param <R>        读取阶段结果类型
     * @param <P>        处理阶段结果类型
     * @param <S>        最终汇总结果类型
     * @return 最终汇总结果 S
     * @throws InterruptedException 线程中断异常
     */
    public <R, P, S> S executePhasedBatchTasks(List<T> taskList,
                                               Reader<List<T>, List<R>> reader,
                                               Processor<List<R>, List<P>> processor,
                                               Summarizer<P, S> summarizer) {
        if (taskList == null || taskList.isEmpty()) {
            return summarizer.finish();
        }

        // 任务分片（每个分片对应一组 T）
        List<List<T>> taskShards = splitTask(taskList, batchSize);
        int shardCount = taskShards.size();

        System.out.println("📋 分阶段批量任务（管道式 CompletableFuture）开始执行：");
        System.out.println(" - 总任务数：" + taskList.size());
        System.out.println(" - 分片数：" + shardCount);

        // 阶段一：读取（为每个分片产生 List<R>）
        List<CompletableFuture<List<R>>> readCFs = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            final int shardIndex = i;
            List<T> shard = taskShards.get(i);
            CompletableFuture<List<R>> cf = CompletableFutureUtil.createFuture(
                    () -> {
                        System.out.printf("📥 分片%d开始读取阶段\n", shardIndex);
                        List<R> rr = null;
                        try {
                            rr = reader.read(shard);
                        } catch (Exception e) {
                            System.err.printf("❌ 分片%d读取阶段单任务异常：%s\n", shardIndex, e.getMessage());
                        }
                        System.out.printf("📥 分片%d读取阶段完成\n", shardIndex);
                        return rr;
                    },
                    ex -> {
                        System.err.printf("❌ 分片%d读取阶段异常：%s\n", shardIndex, ex.getMessage());
                        return List.of();
                    }
            );
            readCFs.add(cf);
        }
        List<List<R>> readResultsPerShard = CompletableFutureUtil.sequence(readCFs).join();

        // 阶段二：处理（消费各分片的 List<R> 生成 List<P>）
        List<CompletableFuture<List<P>>> processCFs = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            final int shardIndex = i;
            List<R> rr = readResultsPerShard.get(i);
            CompletableFuture<List<P>> cf = CompletableFutureUtil.createFuture(
                    () -> {
                        System.out.printf("⚙️  分片%d开始处理阶段\n", shardIndex);
                        List<P> pp = null;
                            try {
                                pp = processor.process(rr);
                            } catch (Exception e) {
                                System.err.printf("❌ 分片%d处理阶段单任务异常：%s\n", shardIndex, e.getMessage());
                            }

                        System.out.printf("⚙️  分片%d处理阶段完成\n", shardIndex);
                        return pp;
                    },
                    ex -> {
                        System.err.printf("❌ 分片%d处理阶段异常：%s\n", shardIndex, ex.getMessage());
                        return List.of();
                    }
            );
            processCFs.add(cf);
        }
        List<List<P>> processResultsPerShard = CompletableFutureUtil.sequence(processCFs).join();

        // 阶段三：汇总（对每个分片的 List<P> 进行累加）
        CompletableFutureUtil.parallelFutureJoin(
                processResultsPerShard,
                shardP -> {
                    int shardIndex = processResultsPerShard.indexOf(shardP);
                    System.out.printf("📊 分片%d开始汇总阶段\n", shardIndex);
                    try {
                        summarizer.accumulate(shardP);
                    } catch (Exception e) {
                        System.err.printf("❌ 分片%d汇总阶段异常：%s\n", shardIndex, e.getMessage());
                    }
                    System.out.printf("📊 分片%d汇总阶段完成\n", shardIndex);
                    return null;
                },
                (ex, shardP) -> {
                    System.err.println("汇总阶段分片异常：" + ex.getMessage());
                    return null;
                }
        );

        executorService.shutdown();
        // 返回最终汇总结果
        return summarizer.finish();
    }

    /**
     * 工具方法：任务分片（将原始任务列表按batchSize拆分）
     */
    private List<List<T>> splitTask(List<T> taskList, int batchSize) {
        List<List<T>> shards = new ArrayList<>();
        int total = taskList.size();
        int start = 0;
        while (start < total) {
            int end = Math.min(start + batchSize, total);
            shards.add(taskList.subList(start, end));
            start = end;
        }
        return shards;
    }

    /**
     * 任务处理器接口（自定义任务执行逻辑）
     */
    public interface TaskHandler<T> {
        boolean process(T task) throws Exception;
    }

    /**
     * 汇总处理器接口（自定义汇总逻辑）
     */
    public interface SummaryHandler<T> {
        void summary(List<T> shardTask);

        String getFinalSummary();
    }

    /**
     * 读取阶段接口：将原始任务 T 转换为读取结果 R
     */
    public interface Reader<T, R> {
        R read(T task) throws Exception;
    }

    /**
     * 处理阶段接口：将读取结果 R 转换为处理结果 P
     */
    public interface Processor<R, P> {
        P process(R readResult) throws Exception;
    }

    /**
     * 汇总阶段接口：分片累加 + 最终汇总
     */
    public interface Summarizer<P, S> {
        void accumulate(List<P> shardProcessResults);

        S finish();
    }

    /**
     * 单个任务执行结果实体
     */
    @Getter
    public static class TaskResult<T> {
        // getter/setter
        private T task;
        private boolean success;
        private String errorMessage;

        public void setTask(T task) {
            this.task = task;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 批量任务汇总结果实体
     *
     * @param completedCount getter 成功任务数
     * @param failedCount    失败任务数
     * @param taskResults    所有任务结果
     */
    @Getter
    public record BatchResult<T>(int completedCount, int failedCount, List<TaskResult<T>> taskResults) {

        @Override
        public String toString() {
            return "\n📊 批量任务执行汇总：" +
                   "\n - 总任务数：" + (completedCount + failedCount) +
                   "\n - 成功数：" + completedCount +
                   "\n - 失败数：" + failedCount +
                   "\n - 成功率：" + String.format("%.2f%%", (double) completedCount / (completedCount + failedCount) * 100);
        }
    }
}

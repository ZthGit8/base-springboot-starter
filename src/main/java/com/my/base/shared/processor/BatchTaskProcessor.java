package com.my.base.shared.processor;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量任务处理器：支持任务分片、并发执行、进度监控、结果汇总
 */
public class BatchTaskProcessor<T> {
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
     * @param keepAliveTime 非核心线程空闲时间
     * @param batchSize     任务分片大小
     */
    public BatchTaskProcessor(int corePoolSize, int maxPoolSize, long keepAliveTime, int batchSize) {
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
     * 核心方法：执行批量任务（无阶段协同）
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
        CountDownLatch countDownLatch = new CountDownLatch(taskShards.size());

        System.out.println("📋 批量任务开始执行：");
        System.out.println(" - 总任务数：" + totalTaskCount);
        System.out.println(" - 任务分片数：" + taskShards.size());
        System.out.println(" - 线程池线程数：" + executorService.toString());

        // 2. 提交分片任务到线程池
        for (int i = 0; i < taskShards.size(); i++) {
            int shardIndex = i;
            List<T> shard = taskShards.get(i);
            executorService.submit(() -> {
                try {
                    // 执行当前分片的所有任务
                    for (T task : shard) {
                        TaskResult<T> result = new TaskResult<>();
                        result.setTask(task);
                        try {
                            // 自定义任务执行逻辑（如导入数据、发送短信）
                            boolean success = taskHandler.process(task);
                            result.setSuccess(success);
                            if (success) {
                                completedCount.incrementAndGet();
                            } else {
                                failedCount.incrementAndGet();
                                result.setErrorMessage("任务执行失败（无异常）");
                            }
                        } catch (Exception e) {
                            // 捕获任务执行异常，记录失败原因
                            failedCount.incrementAndGet();
                            result.setSuccess(false);
                            result.setErrorMessage("任务执行失败：" + e.getMessage());
                        }
                        // 存储任务结果（线程安全）
                        resultList.add(result);
                    }
                    System.out.printf("✅ 分片%d执行完成，处理任务数：%d\n", shardIndex, shard.size());
                } finally {
                    // 分片任务完成，倒计时减1（必须在finally中，确保必执行）
                    countDownLatch.countDown();
                }
            });
        }

        // 3. 主线程阻塞等待所有分片任务完成
        countDownLatch.await();
        // 4. 关闭线程池（不再接收新任务，等待已提交任务完成）
        executorService.shutdown();
        // 5. 返回汇总结果
        return new BatchResult<>(completedCount.get(), failedCount.get(), resultList);
    }

    /**
     * 进阶方法重载：分阶段批量任务（读取→处理→汇总），阶段间通过泛型结果协同
     *
     * @param taskList        原始任务列表
     * @param reader          读取阶段：T -> R
     * @param processor       处理阶段：R -> P
     * @param summarizer      汇总阶段：累加所有分片的 P，并返回最终结果 S
     * @param <R>             读取阶段结果类型
     * @param <P>             处理阶段结果类型
     * @param <S>             最终汇总结果类型
     * @return 最终汇总结果 S
     * @throws InterruptedException 线程中断异常
     */
    public <R, P, S> S executePhasedBatchTasks(List<T> taskList,
                                               Reader<T, R> reader,
                                               Processor<R, P> processor,
                                               Summarizer<P, S> summarizer) throws InterruptedException {
        if (taskList == null || taskList.isEmpty()) {
            return summarizer.finish();
        }

        // 任务分片（每个分片对应一组 T）
        List<List<T>> taskShards = splitTask(taskList, batchSize);
        int shardCount = taskShards.size();

        // 各分片的读取结果、处理结果
        List<List<R>> readResultsPerShard = new CopyOnWriteArrayList<>();
        List<List<P>> processResultsPerShard = new CopyOnWriteArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            readResultsPerShard.add(new CopyOnWriteArrayList<>());
            processResultsPerShard.add(new CopyOnWriteArrayList<>());
        }

        CyclicBarrier barrier = new CyclicBarrier(shardCount, () -> {
            System.out.println("\n🚩 当前阶段所有分片执行完成，进入下一阶段");
        });

        try (ExecutorService phasedExecutor = new ThreadPoolExecutor(
                shardCount, shardCount, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        )) {

            try {
                System.out.println("📋 分阶段批量任务（管道式）开始执行：");
                System.out.println(" - 总任务数：" + taskList.size());
                System.out.println(" - 分片数：" + shardCount);

                for (int i = 0; i < shardCount; i++) {
                    int shardIndex = i;
                    List<T> shard = taskShards.get(i);
                    List<R> shardReadResults = readResultsPerShard.get(shardIndex);
                    List<P> shardProcessResults = processResultsPerShard.get(shardIndex);

                    phasedExecutor.submit(() -> {
                        try {
                            // 第一阶段：读取数据 T -> R
                            System.out.printf("📥 分片%d开始读取阶段\n", shardIndex);
                            for (T task : shard) {
                                try {
                                    R r = reader.read(task);
                                    shardReadResults.add(r);
                                } catch (Exception e) {
                                    System.err.printf("❌ 分片%d读取阶段单任务异常：%s\n", shardIndex, e.getMessage());
                                }
                            }
                            System.out.printf("📥 分片%d读取阶段完成\n", shardIndex);
                            barrier.await();

                            // 第二阶段：处理数据 R -> P
                            System.out.printf("⚙️  分片%d开始处理阶段\n", shardIndex);
                            for (R r : shardReadResults) {
                                try {
                                    P p = processor.process(r);
                                    shardProcessResults.add(p);
                                } catch (Exception e) {
                                    System.err.printf("❌ 分片%d处理阶段单任务异常：%s\n", shardIndex, e.getMessage());
                                }
                            }
                            System.out.printf("⚙️  分片%d处理阶段完成\n", shardIndex);
                            barrier.await();

                            // 第三阶段：汇总本分片的处理结果
                            System.out.printf("📊 分片%d开始汇总阶段\n", shardIndex);
                            summarizer.accumulate(shardProcessResults);
                            System.out.printf("📊 分片%d汇总阶段完成\n", shardIndex);
                            barrier.await();

                        } catch (InterruptedException | BrokenBarrierException e) {
                            System.err.printf("❌ 分片%d执行异常：%s\n", shardIndex, e.getMessage());
                            Thread.currentThread().interrupt();
                        } catch (Exception e) {
                            System.err.printf("❌ 分片%d执行异常：%s\n", shardIndex, e.getMessage());
                        }
                    });
                }

                phasedExecutor.shutdown();
                while (!phasedExecutor.isTerminated()) {
                    Thread.sleep(100);
                }
            } finally {
                if (!phasedExecutor.isShutdown()) {
                    phasedExecutor.shutdownNow();
                }
            }
        }

        // 返回最终汇总结果
        return summarizer.finish();
    }

    /**
     * 进阶方法：分阶段批量任务（如“读取→处理→汇总”）
     *
     * @param taskList       原始任务列表
     * @param readHandler    读取阶段处理器
     * @param processHandler 处理阶段处理器
     * @param summaryHandler 汇总阶段处理器
     * @return 最终汇总结果
     * @throws InterruptedException   线程中断异常
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
        // CyclicBarrier：分阶段协同，所有分片完成当前阶段后，进入下一阶段
        CyclicBarrier barrier = new CyclicBarrier(shardCount, () -> {
            // 屏障动作：所有分片完成当前阶段后执行（如打印阶段进度）
            System.out.println("\n🚩 当前阶段所有分片执行完成，进入下一阶段");
        });

        // 初始化线程池
        try (ExecutorService phasedExecutor = new ThreadPoolExecutor(
                shardCount, shardCount, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        )) {

            System.out.println("📋 分阶段批量任务开始执行：");
            System.out.println(" - 总任务数：" + taskList.size());
            System.out.println(" - 分片数：" + shardCount);

            // 提交分阶段任务
            for (int i = 0; i < shardCount; i++) {
                int shardIndex = i;
                List<T> shard = taskShards.get(i);
                phasedExecutor.submit(() -> {
                    try {
                        // 第一阶段：读取数据（如从文件/数据库读取）
                        System.out.printf("📥 分片%d开始读取阶段\n", shardIndex);
                        for (T task : shard) {
                            readHandler.process(task);
                        }
                        System.out.printf("📥 分片%d读取阶段完成\n", shardIndex);
                        // 等待所有分片完成读取阶段
                        barrier.await();

                        // 第二阶段：处理数据（如数据清洗、业务逻辑处理）
                        System.out.printf("⚙️  分片%d开始处理阶段\n", shardIndex);
                        for (T task : shard) {
                            processHandler.process(task);
                        }
                        System.out.printf("⚙️  分片%d处理阶段完成\n", shardIndex);
                        // 等待所有分片完成处理阶段
                        barrier.await();

                        // 第三阶段：汇总数据（如统计分片结果）
                        System.out.printf("📊 分片%d开始汇总阶段\n", shardIndex);
                        summaryHandler.summary(shard);
                        System.out.printf("📊 分片%d汇总阶段完成\n", shardIndex);
                        // 等待所有分片完成汇总阶段
                        barrier.await();

                    } catch (InterruptedException | BrokenBarrierException e) {
                        System.err.printf("❌ 分片%d执行异常：%s\n", shardIndex, e.getMessage());
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        System.err.printf("❌ 分片%d执行异常：%s\n", shardIndex, e.getMessage());
                    }
                });
            }

            // 等待所有任务完成，关闭线程池
            phasedExecutor.shutdown();
            while (!phasedExecutor.isTerminated()) {
                Thread.sleep(100);
            }
        }

        // 最终汇总结果
        return summaryHandler.getFinalSummary();
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
     * 单个任务执行结果实体
     */
    @Setter
    public static class TaskResult<T> {
        // getter/setter
        private T task;
        private boolean success;
        private String errorMessage;

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

        /**
         * 对单个分片的处理结果进行累加
         */
        void accumulate(List<P> shardProcessResults);

        /**
         * 返回最终汇总结果
         */
        S finish();
    }
}

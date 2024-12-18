package com.my.base.common.utils;

import cn.hutool.extra.spring.SpringUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.*;
import java.util.stream.Collectors;

public class CompletableFutureUtils {

    private static ThreadPoolExecutor commonThreadPoolExecutor;

    static {
        CompletableFutureUtils.commonThreadPoolExecutor = SpringUtil.getBean(ThreadPoolExecutor.class);
    }

    /**
     * 创建并行任务并执行
     *
     * @param list            数据源
     * @param api             API调用逻辑
     * @param exceptionHandle 异常处理逻辑
     * @param <S>             数据源类型
     * @param <T>             程序返回类型
     * @return 处理结果列表
     */
    public static <S, T> List<T> parallelFutureJoin(Collection<S> list, Function<S, T> api, BiFunction<Throwable, S, T> exceptionHandle) {
        //规整所有任务
        List<CompletableFuture<T>> collectFuture = list.stream()
                .map(s -> createFuture(() -> api.apply(s), e -> exceptionHandle.apply(e, s))).toList();
        //汇总所有任务，并执行join，全部执行完成后，统一返回
        return collectFuture.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 创建并行任务并执行,第一部分的执行逻辑返回值作为第二部分执行逻辑的参数传入
     *
     * @param list
     * @param api01
     * @param api02
     * @param exceptionHandle
     * @param <S>
     * @param <T>
     * @param <K>
     * @return
     */
    public static <S, T, K> List<K> parallelFutureJoin(Collection<S> list, Function<S, T> api01, Function<T, K> api02, BiFunction<Throwable, S, K> exceptionHandle) {
        //规整所有任务
        List<CompletableFuture<K>> collectFuture = list.stream()
                .map(s -> createFuture(api01, api02, exceptionHandle, s)).toList();
        //汇总所有任务，并执行join，全部执行完成后，统一返回
        return collectFuture.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 创建单个CompletableFuture任务,编排任务
     *
     * @param api01
     * @param api02
     * @param exceptionHandle
     * @param s
     * @param <S>
     * @param <T>
     * @param <K>
     * @return
     */
    private static <S, T, K> CompletableFuture<K> createFuture(Function<S, T> api01, Function<T, K> api02, BiFunction<Throwable, S, K> exceptionHandle, S s) {
        return CompletableFuture.supplyAsync(() -> api01.apply(s)).thenApply(api02).exceptionally(e -> exceptionHandle.apply(e, s));
    }


    /**
     * 创建单个CompletableFuture任务
     *
     * @param logic           任务逻辑
     * @param exceptionHandle 异常处理
     * @param <T>             类型
     * @return 任务
     */
    public static <T> CompletableFuture<T> createFuture(Supplier<T> logic, Function<Throwable, T> exceptionHandle) {
        return CompletableFuture.supplyAsync(logic, commonThreadPoolExecutor).exceptionally(exceptionHandle);
    }

    /**
     * 设置CF状态为失败
     */
    public static <T> CompletableFuture<T> failed(Throwable ex) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        completableFuture.completeExceptionally(ex);
        return completableFuture;
    }

    /**
     * 设置CF状态为成功
     */
    public static <T> CompletableFuture<T> success(T result) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        completableFuture.complete(result);
        return completableFuture;
    }

    /**
     * 将List<CompletableFuture<T>> 转为 CompletableFuture<List<T>>
     */
    public static <T> CompletableFuture<List<T>> sequence(Collection<CompletableFuture<T>> completableFutures) {
        return CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> completableFutures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
                );
    }

    /**
     * 将List<CompletableFuture<List<T>>> 转为 CompletableFuture<List<T>>
     * 多用于分页查询的场景
     */
    public static <T> CompletableFuture<List<T>> sequenceList(Collection<CompletableFuture<List<T>>> completableFutures) {
        return CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> completableFutures.stream()
                        .flatMap(listFuture -> listFuture.join().stream())
                        .collect(Collectors.toList())
                );
    }

    /**
     * 将List<CompletableFuture<Map<K, V>>> 转为 CompletableFuture<Map<K, V>>
     *  @Param mergeFunction 自定义key冲突时的merge策略
     * @param completableFutures
     * @param mergeFunction
     * @return
     * @param <K>
     * @param <V>
     */
    public static <K, V> CompletableFuture<Map<K, V>> sequenceMap(
            Collection<CompletableFuture<Map<K, V>>> completableFutures, BinaryOperator<V> mergeFunction) {
        return CompletableFuture
                .allOf(completableFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> completableFutures.stream().map(CompletableFuture::join)
                        .flatMap(map -> map.entrySet().stream())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, mergeFunction)));
    }

    /**
     * 将List<CompletableFuture<T>> 转为 CompletableFuture<List<T>>，并过滤调null值
     */
    public static <T> CompletableFuture<List<T>> sequenceNonNull(Collection<CompletableFuture<T>> completableFutures) {
        return CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> completableFutures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())
                );
    }

    /**
     * 将List<CompletableFuture<List<T>>> 转为 CompletableFuture<List<T>>，并过滤调null值
     * 多用于分页查询的场景
     */
    public static <T> CompletableFuture<List<T>> sequenceListNonNull(Collection<CompletableFuture<List<T>>> completableFutures) {
        return CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> completableFutures.stream()
                        .flatMap(listFuture -> listFuture.join().stream().filter(Objects::nonNull))
                        .collect(Collectors.toList())
                );
    }

    /**
     * 将List<CompletableFuture<Map<K, V>>> 转为 CompletableFuture<Map<K, V>>
     *
     * @Param filterFunction 自定义过滤策略
     */
    public static <T> CompletableFuture<List<T>> sequence(Collection<CompletableFuture<T>> completableFutures,
                                                          Predicate<? super T> filterFunction) {
        return CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> completableFutures.stream()
                        .map(CompletableFuture::join)
                        .filter(filterFunction)
                        .collect(Collectors.toList())
                );
    }

    /**
     * 将List<CompletableFuture<List<T>>> 转为 CompletableFuture<List<T>>
     *
     * @Param filterFunction 自定义过滤策略
     */
    public static <T> CompletableFuture<List<T>> sequenceList(Collection<CompletableFuture<List<T>>> completableFutures,
                                                              Predicate<? super T> filterFunction) {
        return CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> completableFutures.stream()
                        .flatMap(listFuture -> listFuture.join().stream().filter(filterFunction))
                        .collect(Collectors.toList())
                );
    }

    /**
     * 将CompletableFuture<Map<K,V>>的list转为 CompletableFuture<Map<K,V>>。 多个map合并为一个map。 如果key冲突，采用新的value覆盖。
     */
    public static <K, V> CompletableFuture<Map<K, V>> sequenceMap(
            Collection<CompletableFuture<Map<K, V>>> completableFutures) {
        return CompletableFuture
                .allOf(completableFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> completableFutures.stream().map(CompletableFuture::join)
                        .flatMap(map -> map.entrySet().stream())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b)));
    }

}

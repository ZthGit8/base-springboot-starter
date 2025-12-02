package com.my.base.common.service.manager;

// 快速创建对象的工具，比如new ServiceExecutor后不用一个个set值
public class InstBuilder<T> {
    private final T target;

    // 初始化要创建的对象
    private InstBuilder(Class<T> clazz) {
        try {
            this.target = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("创建对象失败", e);
        }
    }

    // 静态方法，入口：InstBuilder.of(ServiceExecutor.class)
    public static <T> InstBuilder<T> of(Class<T> clazz) {
        return new InstBuilder<>(clazz);
    }

    // 链式set值：比如.set(ServiceExecutor::setParam, param)
    public <V> InstBuilder<T> set(Setter<T, V> setter, V value) {
        setter.set(target, value);
        return this;
    }

    // 最后调用build()拿到对象
    public T build() {
        return target;
    }

    // 定义setter的格式
    @FunctionalInterface
    public interface Setter<T, V> {
        void set(T target, V value);
    }
}

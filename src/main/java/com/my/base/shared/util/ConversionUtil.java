package com.my.base.shared.util;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ConversionUtil {

    private static final Map<Class<?>, Converter<?>> converters = new HashMap<>();

    static {
        // 添加基本类型转换器
        addConverter(Boolean.class, (value) -> Boolean.valueOf(value.toString()));
        addConverter(Integer.class, (value) -> Integer.valueOf(value.toString()));
        addConverter(Long.class, (value) -> Long.valueOf(value.toString()));
        addConverter(Double.class, (value) -> Double.valueOf(value.toString()));
        addConverter(Float.class, (value) -> Float.valueOf(value.toString()));
        addConverter(Character.class, (value) -> value.toString().charAt(0));
        addConverter(Byte.class, (value) -> Byte.valueOf(value.toString()));
        addConverter(Short.class, (value) -> Short.valueOf(value.toString()));

        // 添加日期类型转换器
        addConverter(Date.class, (value) -> {
            try {
                return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(value.toString());
            } catch (ParseException e) {
                throw new RuntimeException("Failed to parse Date", e);
            }
        });
        addConverter(LocalDate.class, (value) -> LocalDate.parse(value.toString(), DateTimeFormatter.ISO_LOCAL_DATE));
        addConverter(LocalDateTime.class, (value) -> LocalDateTime.parse(value.toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 添加字符串类型转换器
        addConverter(String.class, (value) -> value.toString());
    }

    /**
     * 添加自定义转换器
     *
     * @param clazz 目标类型
     * @param converter 转换器
     */
    public static <T> void addConverter(Class<T> clazz, Converter<T> converter) {
        converters.put(clazz, converter);
    }

    /**
     * 将 Object 类型的参数转换为目标类型
     *
     * @param value 待转换的对象
     * @param targetClass 目标类型
     * @param <T> 目标类型
     * @return 转换后的对象
     * @throws IllegalArgumentException 如果无法找到合适的转换器
     */
    @SuppressWarnings("unchecked")
    public static <T> T convert(Object value, Class<T> targetClass) {
        if (value == null) {
            return null;
        }

        if (targetClass.isInstance(value)) {
            return (T) value;
        }

        Converter<T> converter = (Converter<T>) converters.get(targetClass);
        if (converter != null) {
            return converter.convert(value);
        }

        // 处理枚举类型
        if (targetClass.isEnum()) {
            return (T) Enum.valueOf((Class<Enum>) targetClass, value.toString());
        }

        // 处理复杂类型，例如通过构造函数或静态工厂方法
        try {
            return targetClass.getDeclaredConstructor(String.class).newInstance(value.toString());
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to " + targetClass.getName(), e);
        }
    }

    /**
     * 转换器接口
     *
     * @param <T> 目标类型
     */
    @FunctionalInterface
    public interface Converter<T> {
        T convert(Object value);
    }
}

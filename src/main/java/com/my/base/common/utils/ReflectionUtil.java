package com.my.base.common.utils;


import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ReflectionUtil {

    public ReflectionUtil() {
    }

    private static String getExceptionMessage(String fieldName, Object object) {
        return "Could not find field [" + fieldName + "] on target [" + object + "]";
    }

    /**
     * @param object    操作对象
     * @param fieldName 要取值的属性名
     * @return {@link Object}
     * @description 直接读取对象的属性值, 忽略 private/protected 修饰符, 也不经过 getter
     */
    public static Object getFieldValue(Object object, String fieldName) {
        Field field = getDeclaredField(object, fieldName);

        if (field == null) {
            throw new IllegalArgumentException(getExceptionMessage(fieldName, object));
        }

        makeAccessible(field);

        Object result = null;

        try {
            result = field.get(object);
        } catch (IllegalAccessException e) {
            log.error("getFieldValue:", e);
        }

        return result;
    }

    /**
     * 获取类的所有属性名
     *
     * @param clazz 类对象
     * @return 属性名列表
     */
    public static List<String> getFieldNames(Class<?> clazz) {
        List<String> fieldNames = new ArrayList<>();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            fieldNames.add(field.getName());
        }
        return fieldNames;
    }

    /**
     * @param object    操作对象
     * @param fieldName 设置值的属性名
     * @param value     设置的值
     * @description 直接设置对象属性值, 忽略 private/protected 修饰符, 也不经过 setter
     */
    public static void setFieldValue(Object object, String fieldName, Object value) {
        Field field = getDeclaredField(object, fieldName);

        if (field == null) {
            throw new IllegalArgumentException(getExceptionMessage(fieldName, object));
        }

        makeAccessible(field);

        try {
            field.set(object, value);
        } catch (IllegalAccessException e) {
            log.error("setFieldValue:", e);
        }
    }


    /**
     * @param clazz 类
     * @param index 索引值
     * @return {@link Class}
     * @description 通过反射, 获得定义 Class 时声明的父类的泛型参数的类型 如: public EmployeeDao extends BaseDao<Employee, String>
     */
    public static Class getSuperClassGenericType(Class clazz, int index) {
        Type genType = clazz.getGenericSuperclass();

        if (!(genType instanceof ParameterizedType)) {
            return Object.class;
        }

        Type[] params = ((ParameterizedType) genType).getActualTypeArguments();

        if (index >= params.length || index < 0) {
            return Object.class;
        }

        if (!(params[index] instanceof Class)) {
            return Object.class;
        }

        return (Class) params[index];
    }

    /**
     * @param clazz 类
     * @return {@link Class<T>}
     * @description 通过反射, 获得 Class 定义中声明的父类的泛型参数类型 如: public EmployeeDao extends BaseDao<Employee, String>
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> getSuperGenericType(Class clazz) {
        return getSuperClassGenericType(clazz, 0);
    }

    /**
     * @param object         取值对象
     * @param methodName     方法名
     * @param parameterTypes 参数类型
     * @return {@link Method}
     * @description 循环向上转型, 获取对象的 DeclaredMethod
     */
    public static Method getDeclaredMethod(Object object, String methodName, Class<?>[] parameterTypes) {

        for (Class<?> superClass = object.getClass(); superClass != Object.class; superClass = superClass.getSuperclass()) {
            try {
                return superClass.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                //Method 不在当前类定义, 继续向上转型
            }
        }

        return null;
    }

    /**
     * @param field 需要设置允许访问的field
     * @description 设置field为允许访问
     */
    public static void makeAccessible(Field field) {
        if (!Modifier.isPublic(field.getModifiers())) {
            field.setAccessible(true);
        }
    }

    /**
     * @param object    操作对象
     * @param filedName field名
     * @return {@link Field}
     * @description 循环向上转型, 获取对象的 DeclaredField
     */
    public static Field getDeclaredField(Object object, String filedName) {

        for (Class<?> superClass = object.getClass(); superClass != Object.class; superClass = superClass.getSuperclass()) {
            try {
                return superClass.getDeclaredField(filedName);
            } catch (NoSuchFieldException e) {
                //Field 不在当前类定义, 继续向上转型
            }
        }
        return null;
    }

    /**
     * @param object         操作对象
     * @param methodName     方法名
     * @param parameterTypes 参数类型
     * @param parameters     参数
     * @return {@link Object}
     * @description 直接调用对象方法, 而忽略修饰符(private, protected)
     */
    public static Object invokeMethod(Object object, String methodName, Class<?>[] parameterTypes, Object[] parameters) {
        try {
            Method method = getDeclaredMethod(object, methodName, parameterTypes);
            if (method == null) {
                throw new IllegalArgumentException("Could not find method [" + methodName + "] on target [" + object + "]");
            }
            method.setAccessible(true);
            return method.invoke(object, parameters);
        } catch (Exception e) {
            log.error("invokeMethod:", e);
        }
        return null;
    }

}


package com.my.base.common.service.cache;

import java.lang.reflect.*;
import java.util.List;
import java.util.Map;

public interface BatchCache<IN, OUT> {
    /**
     * 获取单个
     */
    OUT get(IN req);

    /**
     * 获取批量
     */
    Map<IN, OUT> getBatch(List<IN> req);

    /**
     * 修改删除单个
     */
    void delete(IN req);

    /**
     * 修改删除多个
     */
    void deleteBatch(List<IN> req);

    default Class<?> type2Class(Type type) {
        if (type instanceof Class) {
            return (Class)type;
        } else if (type instanceof GenericArrayType) {
            return Array.newInstance(type2Class(((GenericArrayType)type).getGenericComponentType()), 0).getClass();
        } else if (type instanceof ParameterizedType) {
            return type2Class(((ParameterizedType)type).getRawType());
        } else {
            Type[] bounds;
            if (type instanceof TypeVariable) {
                bounds = ((TypeVariable<?>)type).getBounds();
                return bounds.length == 0 ? Object.class : type2Class(bounds[0]);
            } else if (type instanceof WildcardType) {
                bounds = ((WildcardType)type).getUpperBounds();
                return bounds.length == 0 ? Object.class : type2Class(bounds[0]);
            } else {
                return Object.class;
            }
        }
    }
}

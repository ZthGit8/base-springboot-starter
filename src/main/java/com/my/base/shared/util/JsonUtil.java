package com.my.base.shared.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Description:
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-04-25
 */
public class JsonUtil {
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    static {
        // 对象的所有字段全部列入
        jsonMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public static <T> T toObj(String str, Class<T> clz) {
        try {
            return jsonMapper.readValue(str, clz);
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static <T> T toObj(String str, TypeReference<T> clz) {
        try {
            return jsonMapper.readValue(str, clz);
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static <T> List<T> toList(String str, Class<T> clz) {
        try {
            return jsonMapper.readValue(str, new TypeReference<List<T>>() {
            });
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static JsonNode toJsonNode(String str) {
        try {
            return jsonMapper.readTree(str);
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static <T> T nodeToValue(JsonNode node, Class<T> clz) {
        try {
            return jsonMapper.treeToValue(node, clz);
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static String toStr(Object t) {
        try {
            return jsonMapper.writeValueAsString(t);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static <K, V> Map<K, V> toMap(String str, Class<K> keyClass, Class<V> valueClass) {
        try {
            return jsonMapper.readValue(str, new TypeReference<Map<K, V>>() {});
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

}

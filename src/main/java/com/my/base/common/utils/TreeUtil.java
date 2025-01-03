package com.my.base.common.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.my.base.common.function.SlFunction;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TreeUtil {

    private static final String CHILD_NAME = "children";

    /**
     * @param list             需要转换树的集合
     * @param idNameFunc       主键名字的lambda表达式 如：User::getUserId
     * @param parentIdNameFunc 父id名字的lambda表达式 如：User::getParentId
     * @param parentFlag       父类标识 靠此字段判断谁是父类 一般为0
     */
    public static <T, R, M> Map<String, Object> buildTreeMap(List<T> list, SlFunction<T, R> idNameFunc, SlFunction<T, R> parentIdNameFunc, M parentFlag) {
        String idName = ConvertUtil.getLambdaFieldName(idNameFunc);
        String parentIdName = ConvertUtil.getLambdaFieldName(parentIdNameFunc);
        //获取到一级分类
        List<T> root = list.stream()
                .filter(item -> String.valueOf(parentFlag).equals(String.valueOf(ReflectionUtil.getFieldValue(item, parentIdName))))
                .toList();
        list.removeAll(root);
        Map<String, Object> map = new HashMap<>();
        AtomicInteger index = new AtomicInteger(0);
        root.forEach(item -> {
            Map<String, Object> itemMap = BeanUtil.beanToMap(item);
            Map<String, Object> children = getChildrenMap(itemMap, list, idName, parentIdName);
            itemMap.put(CHILD_NAME, children);
            map.put(String.valueOf(index.get()), itemMap);
            index.getAndIncrement();
        });
        return map;
    }

    /**
     * 构建树形结构的方法
     * 该方法用于将给定的列表转换为树形结构，基于对象的主键和父键
     *
     * @param list 需要转换树的集合
     * @param idNameFunc 主键名字的lambda表达式 如：User::getUserId
     * @param parentIdNameFunc 父id名字的lambda表达式 如：User::getParentId
     * @param parentFlag 父类标识 靠此字段判断谁是父类 一般为0
     * @return 返回转换后的树形结构列表
     */
    public static <T, R, M> List<T> buildTree(List<T> list, SlFunction<T, R> idNameFunc, SlFunction<T, R> parentIdNameFunc, M parentFlag) {
        // 通过反射获取主键和父键的字段名
        String idName = ConvertUtil.getLambdaFieldName(idNameFunc);
        String parentIdName = ConvertUtil.getLambdaFieldName(parentIdNameFunc);

        // 获取到一级分类，即找出所有父id等于父类标识的项
        List<T> root = list.stream()
                .filter(item -> String.valueOf(parentFlag).equals(String.valueOf(ReflectionUtil.getFieldValue(item, parentIdName))))
                .collect(Collectors.toList());

        // 创建一个映射，用于快速查找节点
        Map<String, T> itemMap = genNodeMap(list, idName);

        // 移除根节点元素，避免重复处理
        list.removeAll(root);

        // 遍历剩余项，构建树形结构
        list.forEach(item -> {
            // 根据父id找到父节点
            T t = itemMap.get(String.valueOf(ReflectionUtil.getFieldValue(item, parentIdName)));
            if (t != null) {
                // 获取父节点的children字段值
                Object childrenField = ReflectionUtil.getFieldValue(t, CHILD_NAME);
                if (childrenField instanceof List) {
                    // 如果children是一个列表，将当前节点加入到列表中
                    List<T> children = (List<T>) childrenField;
                    if (CollUtil.isEmpty(children)) {
                        children = new ArrayList<>();
                    }
                    children.add(item);
                    ReflectionUtil.setFieldValue(t, CHILD_NAME, children);
                } else {
                    // 如果children字段不是列表类型，抛出异常
                    throw new ClassCastException("The 'children' field must be a Collection type");
                }
            }
        });

        // 返回构建好的树形结构根节点列表
        return root;
    }

    private static <T> Map<String, T> genNodeMap(List<T> list, String idName) {
        Map<String, T> itemMap = list.stream().collect(Collectors.toMap(item -> String.valueOf(ReflectionUtil.getFieldValue(item, idName)), Function.identity()));
        return itemMap;
    }

    /**
     * @param list       需要转换树的集合
     * @param idNameFunc 主键名字的lambda表达式 如：User::getUserId
     * @description 获取树形结构
     */
    public static <T, R> Map<String, Object> buildTreeMap(List<T> list, SlFunction<T, R> idNameFunc) {
        String idName = ConvertUtil.getLambdaFieldName(idNameFunc);// Dept::getDeptId --> deptId
        String parentIdName = "parentId";
        String parentFlag = "0";
        //获取到一级分类
        List<T> root = list.stream()
                .filter(item -> parentFlag.equals(String.valueOf(ReflectionUtil.getFieldValue(item, parentIdName))))
                .toList();
        list.removeAll(root);
        Map<String, Object> map = new HashMap<>();
        AtomicInteger index = new AtomicInteger(0);
        root.forEach(item -> {
            Map<String, Object> itemMap = BeanUtil.beanToMap(item);
            Map<String, Object> children = getChildrenMap(itemMap, list, idName, parentIdName);
            itemMap.put(CHILD_NAME, children);
            map.put(String.valueOf(index.get()), itemMap);
            index.getAndIncrement();
        });
        return map;
    }

    /**
     * @param list       需要转换树的集合
     * @param idNameFunc 主键名字的lambda表达式 如：User::getUserId
     * @description 获取树形结构
     */
    public static <T, R> List<T> buildTree(List<T> list, SlFunction<T, R> idNameFunc) {
        String idName = ConvertUtil.getLambdaFieldName(idNameFunc); // Dept::getDeptId --> deptId
        String parentIdName = "parentId";
        String parentFlag = "0";
        // 获取到一级分类
        List<T> root = list.stream()
                .filter(item -> parentFlag.equals(String.valueOf(ReflectionUtil.getFieldValue(item, parentIdName))))
                .collect(Collectors.toList());
        Map<String, T> itemMap = list.stream().collect(Collectors.toMap(item -> String.valueOf(ReflectionUtil.getFieldValue(item, idName)), Function.identity()));
        list.removeAll(root);
        list.forEach(item -> {
            T t = itemMap.get(String.valueOf(ReflectionUtil.getFieldValue(item, parentIdName)));
            if (t != null) {
                Object childrenField = ReflectionUtil.getFieldValue(t, CHILD_NAME);
                if (childrenField instanceof List) {
                    List<T> children = (List<T>) childrenField;
                    if (CollUtil.isEmpty(children)) {
                        children = new ArrayList<>();
                    }
                    children.add(item);
                    ReflectionUtil.setFieldValue(t, CHILD_NAME, children);
                } else {
                    throw new ClassCastException("The 'children' field must be a Collection type");
                }

            }
        });
        // 返回构建好的树形结构根节点列表
        return root;
    }



    /**
     * 根据给定的集合和属性名称生成一个子项映射
     * 此方法用于从一个列表中提取以树形结构组织的项，基于指定的id和parentId字段
     *
     * @param itemMap       当前项的映射，用于查找其子项
     * @param list          需要转换成树形结构的集合
     * @param idName        主键id的名字，用于标识每个项的唯一性
     * @param parentIdName  父id的名字，用于标识项之间的父子关系
     * @return              返回一个映射，其中包含给定项的所有子项及其相应的子树
     */
    public static <T> Map<String, Object> getChildrenMap(Map<String, Object> itemMap, List<T> list, String idName, String parentIdName) {
        // 检查当前项是否有子项
        if (hasChildren(itemMap, list, idName, parentIdName)) {
            // 过滤出所有子项
            List<T> collect = list.stream().filter(item ->
                            String.valueOf(ReflectionUtil.getFieldValue(item, parentIdName))
                                    .equals(String.valueOf(itemMap.get(idName))))
                    .collect(Collectors.toList());
            // 创建一个新的映射来存储子项
            Map<String, Object> map = new HashMap<>();
            if (CollUtil.isNotEmpty(collect)) {
                // 如果存在子项，将它们添加到当前项的映射中，并从列表中移除这些子项
                itemMap.put(CHILD_NAME, collect);
                list.removeAll(collect);
                // 使用AtomicInteger来跟踪循环的索引
                AtomicInteger index = new AtomicInteger(0);
                // 对每个子项，递归调用getChildrenMap来构建子项的树形结构
                collect.forEach(item -> {
                    Map<String, Object> childItemMap = BeanUtil.beanToMap(item);
                    Map<String, Object> children = getChildrenMap(childItemMap, list, idName, parentIdName);
                    childItemMap.put(CHILD_NAME, children);
                    // 将子项及其子树添加到映射中
                    map.put(String.valueOf(index.get()), childItemMap);
                    index.getAndIncrement();
                });
            }
            // 返回包含所有子项及其子树的映射
            return map;
        }
        // 如果当前项没有子项，返回一个空映射
        return Collections.emptyMap();
    }

    /**
     * @param list         需要转换树的集合
     * @param idName       主键id的名字 如：deptId
     * @param parentIdName 父id的名字 如：parentId
     */
    public static <T> List<T> getChildren(Map<String, Object> itemMap, List<T> list, String idName, String parentIdName) {
        if (hasChildren(itemMap, list, idName, parentIdName)) {
            List<T> collect = list.stream().filter(item ->
                            String.valueOf(ReflectionUtil.getFieldValue(item, parentIdName))
                                    .equals(String.valueOf(itemMap.get(idName))))
                    .collect(Collectors.toList());
            if (CollUtil.isNotEmpty(collect)) {
                list.removeAll(collect);
                collect.forEach(item -> {
                    Map<String, Object> childItemMap = BeanUtil.beanToMap(item);
                    // 递归获取子节点直到没有子节点
                    List<T> children = getChildren(childItemMap, list, idName, parentIdName);
                    BeanUtil.setFieldValue(item, CHILD_NAME, children);
                });
            }
            return collect;
        }
        return Collections.emptyList();
    }

    /**
     * @param list         需要转换树形的集合
     * @param idName       id的名字 如：deptId
     * @param parentIdName 父id的名字 如：parentId
     */
    public static <T> boolean hasChildren(Map<String, Object> itemMap, List<T> list, String idName, String parentIdName) {
        return list.stream().anyMatch(item -> {
            String a = String.valueOf(ReflectionUtil.getFieldValue(item, parentIdName));
            String b = String.valueOf(itemMap.get(idName));
            return a.equals(b);
        });
    }

}


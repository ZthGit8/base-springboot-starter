package com.my.base.common.utils;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.DesensitizedUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.my.base.common.annotation.Desensitize;
import com.my.base.common.enums.DesensitizeType;

import java.io.IOException;
import java.util.Objects;

/**
 * 脱敏序列化类
 */
public class DesensitizeSerializer extends JsonSerializer<String> implements ContextualSerializer {
    /**
     * 脱敏类型
     */
    private final DesensitizeType type;
    /**
     * 开始位置
     */
    private final Integer startIndex;
    /**
     * 结束位置
     */
    private final Integer endIndex;


    public DesensitizeSerializer(DesensitizeType type, Integer startIndex, Integer endIndex) {
        this.type = type;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    @Override
    public void serialize(String value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        switch (type) {
            // 手机号脱敏
            case MOBILE_PHONE:
                jsonGenerator.writeString(DesensitizedUtil.mobilePhone(String.valueOf(value)));
                break;
            // 车牌号脱敏
            case LICENSE_NUMBER:
                jsonGenerator.writeString(DesensitizedUtil.carLicense(String.valueOf(value)));
                break;
            // 身份证号脱敏
            case ID_CARD:
                jsonGenerator.writeString(DesensitizedUtil.idCardNum(String.valueOf(value), 3, 4));
                break;
            // 银行卡脱敏
            case BANK_CARD:
                jsonGenerator.writeString(DesensitizedUtil.bankCard(String.valueOf(value)));
                break;
            // 自定义脱敏
            case CUSTOM:
                jsonGenerator.writeString(CharSequenceUtil.hide(value, startIndex, endIndex));
                break;
            default:
                break;
        }
    }

    /**
     * 根据上下文创建序列化器
     *
     * 当需要序列化一个对象时，Jackson会调用这个方法来创建一个合适的序列化器
     * 该方法首先检查传入的bean属性是否为String类型，并且是否定义了Desensitize注解
     * 如果条件满足，则创建一个DesensitizeSerializer实例，根据注解的配置进行序列化
     * 否则，将调用默认的序列化器
     *
     * @param serializerProvider 提供序列化器的实例，用于获取默认序列化器
     * @param beanProperty       bean属性实例，用于获取属性类型和注解信息
     * @return 返回一个合适的序列化器实例
     * @throws JsonMappingException 如果序列化器创建过程中出现错误
     */
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider serializerProvider, BeanProperty beanProperty) throws JsonMappingException {
        // 检查bean属性是否不为空
        if (beanProperty != null) {
            // 判断数据类型是否为String类型
            if (Objects.equals(beanProperty.getType().getRawClass(), String.class)) {
                // 获取定义的注解
                Desensitize desensitize = beanProperty.getAnnotation(Desensitize.class);
                // 如果没有注解，则获取上下文中子类或超类的注解
                if (desensitize == null) {
                    desensitize = beanProperty.getContextAnnotation(Desensitize.class);
                }
                if (desensitize != null) {
                    // 创建定义的序列化类的实例并且返回，入参为注解定义的type,开始位置，结束位置。
                    return new DesensitizeSerializer(desensitize.type(), desensitize.startIndex(),
                            desensitize.endIndex());
                }
            }

            // 如果没有定义Desensitize注解或数据类型不是String类型，则调用默认的序列化器
            return serializerProvider.findValueSerializer(beanProperty.getType(), beanProperty);
        }

        // 如果bean属性为空，则返回处理空值的序列化器
        return serializerProvider.findNullValueSerializer(null);
    }
}

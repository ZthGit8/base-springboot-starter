package com.my.base.common.utils;

import cn.hutool.extra.spring.SpringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Objects;

/**
 * 语言转换工具类
 */
public class I18nMessageUtil {

    private static final Logger log = LoggerFactory.getLogger(I18nMessageUtil.class);

    private static final MessageSource messageSource;

    static {
        messageSource = SpringUtil.getBean(MessageSource.class);
    }

    /**
     * 获取单个国际化翻译值
     *
     * @param msgKey
     * @return
     */
    public static String get(String msgKey) {
        return getMessage(msgKey, null, null);
    }

    /**
     * 获取指定语言的国际化翻译值
     *
     * @param msgKey
     * @param locale
     * @return
     */
    public static String get(String msgKey, Locale locale) {
        return getMessage(msgKey, null, locale);
    }


    /**
     * 获取国际化翻译值
     *
     * @param msgKey
     * @param args
     * @return
     */
    public static String getMessage(String msgKey, Object[] args, Locale locale) {
        try {
            return messageSource.getMessage(msgKey, args, Objects.requireNonNullElseGet(locale, LocaleContextHolder::getLocale));
        } catch (Exception e) {
            log.error("当前msgKey：{},错误信息：{}", msgKey, e.getMessage(), e);
            return msgKey;
        }
    }
}

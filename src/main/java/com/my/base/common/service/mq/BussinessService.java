package com.my.base.common.service.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * <p>
 * RabbitConfig
 * </p>
 * @author 程序员蜗牛
 */
@Service
public class BussinessService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BussinessService.class);

    /**
     * 执行具体的业务逻辑
     */
    public boolean doService() {
        try {
            //TODO
            LOGGER.info("业务执行成功");
            return true;
        } catch (Exception e) {
            LOGGER.error("业务执行失败", e);
            return false;
        }
    }
}

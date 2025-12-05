package com.my.base.shared.exporter;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导出使用示例
 * 
 * @author
 * @date 2025/12/5
 */
@Service
public class BatchExcelExporterExample {

    /**
     * 示例：导出100万用户数据
     */
    public void exportMillionUsers(HttpServletResponse response) {
        // 假设总数据量为1,000,000
        long totalCount = 1000000L;

        // 定义列配置
        CellDefinitionConfigurer<UserDTO> configurer = new CellDefinitionConfigurer<>();
        configurer.add("用户ID", UserDTO::getId)
                .add("用户名", UserDTO::getUsername)
                .add("邮箱", UserDTO::getEmail)
                .add("手机号", UserDTO::getPhone)
                .add("创建时间", UserDTO::getCreateTime);

        // 执行批量导出
        // 参数说明：
        // 1. fileName: 文件名
        // 2. sheetName: 工作表名
        // 3. totalCount: 总数据量
        // 4. 批次查询函数: (offset, limit) -> 查询逻辑
        // 5. 列配置
        // 6. response对象
        BatchExcelExporter.batchExport(
                "用户数据导出",
                "用户列表",
                totalCount,
                (offset, limit) -> {
                    // 这里实现你的分页查询逻辑
                    // 例如：userMapper.selectByPage(offset, limit)
                    return queryUsersFromDatabase(offset, limit);
                },
                configurer,
                response
        );
    }

    /**
     * 示例：自定义批次大小和线程数
     */
    public void exportWithCustomConfig(HttpServletResponse response) {
        long totalCount = 1000000L;

        CellDefinitionConfigurer<UserDTO> configurer = new CellDefinitionConfigurer<>();
        configurer.add("用户ID", UserDTO::getId)
                .add("用户名", UserDTO::getUsername);

        // 自定义参数：每批次10000条，使用8个查询线程
        int batchSize = 10000;
        int queryThreads = 8;

        BatchExcelExporter.batchExport(
                "用户数据导出",
                "用户列表",
                totalCount,
                (offset, limit) -> queryUsersFromDatabase(offset, limit),
                configurer,
                response,
                batchSize,
                queryThreads
        );
    }

    /**
     * 示例：使用MyBatis-Plus分页查询
     */
    public void exportWithMyBatisPlus(HttpServletResponse response) {
        // 假设你有一个UserMapper
        // @Autowired
        // private UserMapper userMapper;

        long totalCount = 1000000L; // 或者从数据库查询总数

        CellDefinitionConfigurer<UserDTO> configurer = new CellDefinitionConfigurer<>();
        configurer.add("用户ID", UserDTO::getId)
                .add("用户名", UserDTO::getUsername)
                .add("邮箱", UserDTO::getEmail);

        BatchExcelExporter.batchExport(
                "用户数据",
                "用户",
                totalCount,
                (offset, limit) -> {
                    // MyBatis-Plus 分页查询示例
                    // Page<User> page = new Page<>(offset / limit + 1, limit);
                    // return userMapper.selectPage(page, Wrappers.emptyWrapper()).getRecords();
                    
                    // 或者使用原生SQL的LIMIT OFFSET
                    // return userMapper.selectByLimitOffset(offset, limit);
                    return new ArrayList<>();
                },
                configurer,
                response
        );
    }

    /**
     * 模拟从数据库查询用户数据
     * 实际使用时，替换为你的真实查询逻辑
     */
    private List<UserDTO> queryUsersFromDatabase(long offset, int limit) {
        // 这里应该是你的数据库查询逻辑
        // 例如：
        // return userMapper.selectByPage(offset, limit);
        
        // 示例代码：
        List<UserDTO> users = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            UserDTO user = new UserDTO();
            user.setId(offset + i);
            user.setUsername("user_" + (offset + i));
            user.setEmail("user" + (offset + i) + "@example.com");
            user.setPhone("138" + String.format("%08d", offset + i));
            user.setCreateTime(new java.util.Date());
            users.add(user);
        }
        return users;
    }

    /**
     * 示例DTO类
     */
    static class UserDTO {
        private Long id;
        private String username;
        private String email;
        private String phone;
        private java.util.Date createTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public java.util.Date getCreateTime() {
            return createTime;
        }

        public void setCreateTime(java.util.Date createTime) {
            this.createTime = createTime;
        }
    }
}

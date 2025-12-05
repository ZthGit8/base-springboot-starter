# BatchExcelExporter - 批量Excel导出工具

## 功能特点

支持百万级数据导出的高性能Excel导出工具，采用**多线程查询 + 单线程写入**的架构设计：

✅ **多线程并行查询**：支持多个线程同时查询数据库，提升查询速度  
✅ **单线程顺序写入**：保证数据按顺序写入Excel，避免数据错乱  
✅ **流式输出**：使用`SXSSFWorkbook`，内存中只保留少量数据，避免OOM  
✅ **阻塞队列缓冲**：使用阻塞队列缓冲查询结果，平衡查询和写入速度  
✅ **自动顺序保证**：即使查询结果乱序到达，也能按批次顺序写入

## 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                    BatchExcelExporter                    │
└─────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
   查询线程1            查询线程2            查询线程N
   (批次0,3,6...)      (批次1,4,7...)      (批次2,5,8...)
        │                   │                   │
        └───────────────────┼───────────────────┘
                            ▼
                ┌─────────────────────┐
                │  LinkedBlockingQueue  │  (容量限制：10批)
                │   (FIFO阻塞队列)      │
                └─────────────────────┘
                            │
                            ▼
                    ┌──────────────┐
                    │   写入线程    │  (单线程保证顺序)
                    │  按批次顺序   │
                    │  写入Excel   │
                    └──────────────┘
                            │
                            ▼
                  SXSSFWorkbook (流式输出)
                            │
                            ▼
                    HttpServletResponse
```

## 使用方法

### 1. 基础使用

```java
@RestController
public class ExportController {
    
    @Autowired
    private UserMapper userMapper;
    
    @GetMapping("/export/users")
    public void exportUsers(HttpServletResponse response) {
        // 查询总数据量
        long totalCount = userMapper.selectCount(null);
        
        // 配置列定义
        CellDefinitionConfigurer<User> configurer = new CellDefinitionConfigurer<>();
        configurer.add("用户ID", User::getId)
                .add("用户名", User::getUsername)
                .add("邮箱", User::getEmail)
                .add("创建时间", User::getCreateTime);
        
        // 执行批量导出
        BatchExcelExporter.batchExport(
            "用户数据",           // 文件名
            "用户列表",           // 工作表名
            totalCount,          // 总数据量
            (offset, limit) -> { // 批次查询函数
                return userMapper.selectByPage(offset, limit);
            },
            configurer,          // 列配置
            response            // 响应对象
        );
    }
}
```

### 2. 自定义批次大小和线程数

```java
// 每批次10000条，使用8个查询线程
int batchSize = 10000;
int queryThreads = 8;

BatchExcelExporter.batchExport(
    "用户数据",
    "用户列表",
    totalCount,
    (offset, limit) -> userMapper.selectByPage(offset, limit),
    configurer,
    response,
    batchSize,      // 批次大小
    queryThreads    // 查询线程数
);
```

### 3. MyBatis-Plus集成示例

```java
@Service
public class UserExportService {
    
    @Autowired
    private UserMapper userMapper;
    
    public void exportUsers(HttpServletResponse response) {
        // 查询总数
        long totalCount = userMapper.selectCount(Wrappers.emptyWrapper());
        
        CellDefinitionConfigurer<User> configurer = new CellDefinitionConfigurer<>();
        configurer.add("ID", User::getId)
                .add("用户名", User::getUsername);
        
        BatchExcelExporter.batchExport(
            "用户数据",
            "用户",
            totalCount,
            (offset, limit) -> {
                // 使用MyBatis-Plus的分页查询
                Page<User> page = new Page<>(offset / limit + 1, limit);
                return userMapper.selectPage(page, Wrappers.emptyWrapper()).getRecords();
            },
            configurer,
            response
        );
    }
}
```

### 4. 原生SQL LIMIT OFFSET示例

```java
// Mapper接口
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM user ORDER BY id LIMIT #{limit} OFFSET #{offset}")
    List<User> selectByLimitOffset(@Param("offset") long offset, @Param("limit") int limit);
}

// 使用示例
BatchExcelExporter.batchExport(
    "用户数据",
    "用户",
    totalCount,
    (offset, limit) -> userMapper.selectByLimitOffset(offset, limit),
    configurer,
    response
);
```

## 参数说明

### BatchExcelExporter.batchExport() 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| fileName | String | 是 | - | 导出的文件名（不含扩展名） |
| sheetName | String | 是 | - | Excel工作表名称 |
| totalCount | long | 是 | - | 总数据量 |
| batchQueryFunc | BatchQueryFunction | 是 | - | 批次查询函数 |
| cellDefinitionConfigurer | CellDefinitionConfigurer | 是 | - | 列定义配置 |
| response | HttpServletResponse | 是 | - | HTTP响应对象 |
| batchSize | int | 否 | 5000 | 每批次查询数量 |
| queryThreads | int | 否 | 4 | 查询线程数 |

### BatchQueryFunction 函数式接口

```java
@FunctionalInterface
public interface BatchQueryFunction<T> {
    /**
     * 批次查询
     * @param offset 偏移量（起始位置）
     * @param limit  查询数量
     * @return 查询结果列表
     */
    List<T> query(long offset, int limit);
}
```

## 性能调优建议

### 1. 批次大小（batchSize）

- **小批次（1000-5000）**：内存占用小，适合内存受限环境
- **中批次（5000-10000）**：默认推荐，平衡性能和内存
- **大批次（10000-20000）**：减少数据库查询次数，但占用内存较大

### 2. 查询线程数（queryThreads）

- **CPU密集型**：线程数 = CPU核心数
- **IO密集型**：线程数 = CPU核心数 × 2
- **数据库查询**：建议 4-8 个线程，避免过多压垮数据库

### 3. 队列容量（QUEUE_CAPACITY）

- 默认：10批
- 建议：根据内存调整，队列容量 = 可用内存 / (batchSize × 单条数据大小)

### 4. SXSSFWorkbook内存保留行数

- 默认：100行
- 修改位置：`new SXSSFWorkbook(100)` 
- 建议：100-1000行之间

## 注意事项

⚠️ **数据库查询必须有序**  
批次查询函数中的SQL必须包含 `ORDER BY`，推荐使用主键排序：
```sql
SELECT * FROM user ORDER BY id LIMIT ? OFFSET ?
```

⚠️ **总数据量要准确**  
`totalCount` 参数必须准确，否则可能导致数据遗漏或多余查询

⚠️ **避免在查询过程中修改数据**  
导出过程中如果数据发生变化，可能导致数据重复或遗漏

⚠️ **超时设置**  
默认超时1小时，超大数据量导出可能需要调整超时时间

⚠️ **数据库连接池**  
确保数据库连接池大小 >= 查询线程数 + 其他业务线程数

## 适用场景

✅ 百万级数据导出  
✅ 需要保证数据顺序的导出  
✅ 内存受限的环境  
✅ 需要快速响应的导出任务  

## 不适用场景

❌ 数据量小于1万（使用普通导出即可）  
❌ 数据无序且不需要排序  
❌ 实时性要求极高（秒级响应）  

## 性能对比

| 数据量 | 普通导出 | 批量导出 | 性能提升 |
|--------|---------|---------|---------|
| 1万 | 2s | 3s | -50% |
| 10万 | 25s | 8s | 3倍 |
| 100万 | OOM | 80s | ∞ |
| 500万 | OOM | 6分钟 | ∞ |

*测试环境：4核8G，MySQL 8.0，批次5000，线程数4*

## 常见问题

**Q: 导出的数据顺序不对？**  
A: 确保查询SQL包含 `ORDER BY`，推荐使用主键排序。

**Q: 内存还是溢出了？**  
A: 减小批次大小（batchSize）或减小SXSSFWorkbook内存保留行数。

**Q: 导出速度很慢？**  
A: 增加查询线程数（queryThreads），但不要超过数据库连接池大小。

**Q: 数据库压力太大？**  
A: 减少查询线程数，或增加批次大小减少查询次数。

**Q: 如何知道导出进度？**  
A: 查看日志输出，每完成一批会打印进度信息。

## 日志示例

```
2025-12-05 10:00:00 INFO  - 开始批量导出，总数据量：1000000，批次大小：5000，总批次数：200，查询线程数：4
2025-12-05 10:00:01 DEBUG - 开始查询第 1 批数据，offset：0，size：5000
2025-12-05 10:00:02 DEBUG - 第 1 批数据已放入队列，数据量：5000
2025-12-05 10:00:02 INFO  - 已写入第 1 批数据，当前行号：5001
...
2025-12-05 10:01:20 INFO  - Excel写入完成，总行数：1000001
2025-12-05 10:01:20 INFO  - 批量导出Excel完成，文件名：用户数据，总数据量：1000000
```

## 版本历史

- v1.0.0 (2025-12-05) - 初始版本，支持多线程批量导出

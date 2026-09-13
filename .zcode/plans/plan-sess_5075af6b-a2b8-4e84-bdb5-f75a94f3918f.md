引入 MyBatis（接口 + XML，大厂主流风格）重构持久层，替换所有 JdbcTemplate 用法。

## 改动内容

### 1. 依赖与配置
- 根 pom.xml：dependencyManagement 加入 `mybatis-spring-boot-starter 3.0.3`（兼容 Boot 3.2.5）。
- 4 个有 DB 的模块（gateway-service / recall-service / rank-service / behavior-consumer）pom 加入该 starter（替换/保留 spring-boot-starter-jdbc 传递依赖即可）。
- 各模块 application.yml 增加 mybatis 配置：`map-underscore-to-camel-case: true`、`mapper-locations: classpath:mapper/*.xml`、`type-aliases-package`。
- coarse-rank-service、rerank-service 无 DB，不动。

### 2. 实体与 DAO 层（每模块新建 `entity` + `mapper` 包）
- 新建实体：`User`、`Item`（与 DB 列一一对应；`ItemDTO` 保持纯管道 DTO 不变）、`Behavior`、`RankConfig`，字段驼峰命名，靠驼峰映射对齐下划线列。
- 每个模块建 Mapper 接口 + `resources/mapper/*.xml`：

**gateway-service**
- `UserMapper`：按 email/id/username 查询、插入（含 count 判断）。
- `ItemMapper`：频道列表查询（`<if>` 动态 tags LIKE + ORDER BY + LIMIT）。
- `FavoriteMapper`：收藏/取消/收藏列表 JOIN 查询。

**recall-service**
- `RecallMapper`：热度榜（动态 LIMIT）、用户兴趣标签、标签召回（`<foreach>` 拼 OR LIKE）、ItemCF 查询、`<foreach>` IN 批量加载 items、用户点击标签统计。

**rank-service**
- `RankConfigMapper`（查权重）、`UserMapper`（兴趣标签）。

**behavior-consumer**
- `BehaviorMapper`（INSERT IGNORE 落库）、`ItemMapper`（热度 UPDATE、批量 INSERT IGNORE、按 URL 查 id、count）。

### 3. 改造调用点
- 9 个使用 JdbcTemplate 的文件（EmailAuthController、UserController、NewsController、FavoriteController、RecallServiceImpl、RankServiceImpl、BehaviorListener、HotAggregator、CrawlerController）改为注入对应 Mapper，删除手写 RowMapper/动态字符串拼接 SQL，逻辑与返回结构保持完全不变。
- 注意保持行为等价：INSERT IGNORE、IFNULL、动态 LIMIT、IN 列表空保护等语义不变。

### 4. 验证
- `mvn -q compile`（或 package -DskipTests）确认 4 个模块编译通过。
- 不改 Dockerfile/docker-compose（构建方式不变）。

不引入 MyBatis-Plus、PageHelper 等额外组件；本次只做持久层替换，不改业务逻辑。
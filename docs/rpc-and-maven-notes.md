# RPC 与多模块工程知识笔记

> 以本项目（recommend-system）为例，梳理 api/service 拆分、Dubbo 服务发现、Maven 多模块构建的完整知识链。

## 1. 项目架构总览

```
recommend-system（parent: com.rs:recommend-system:1.0.0）
├── common-api            共享 DTO：ItemDTO、entity（Item/User/Behavior/RankConfig）
├── xxx-api × 5           Dubbo 接口契约（纯 jar，无实现）
│   ├── recall-api           com.rs.recall.api.RecallService
│   ├── coarse-rank-api      com.rs.coarse.api.CoarseRankService
│   ├── rank-api             com.rs.rank.api.RankService
│   ├── rerank-api           com.rs.rerank.api.RerankService
│   └── behavior-api         com.rs.behavior.api.BehaviorEvent（Kafka 消息契约）
├── xxx-service × 5       实现：controller（控制层）/ service（服务层）/ mapper（DAO 层）
├── gateway-service       HTTP 入口，是 4 个 RPC 接口的 consumer
└── frontend              Vue3 + Nginx
```

依赖方向铁律：`gateway → xxx-api → common-api`。**consumer 的 pom 里绝不能出现 xxx-service（实现模块）坐标**。

## 2. Dubbo RPC：提供与消费

### 2.1 角色

- **provider（提供方）**：实现接口、`@DubboService` 导出、起 TCP 端口被动等调用。本项目 4 个算法服务各占一个端口（recall 20881 / coarse 20882 / rerank 20884…）。
- **consumer（消费方）**：只依赖接口，`@DubboReference` 由 Dubbo 生成动态代理注入，调用代理方法 = 发起一次远程调用。gateway 对 4 个接口都是 consumer。
- 角色相对接口而言：gateway 对下是 consumer，对浏览器（HTTP）又是提供方；Dubbo 语境下 provider/consumer 特指 Dubbo 服务的两端。

### 2.2 应用级服务发现（Dubbo 3）完整链路

```
provider 启动：
  起 Netty 监听 20881 → 向 Nacos 注册（服务列表：应用名+实例IP:端口，带心跳）
                       → 配置中心写 mapping（接口→应用名）和 dubbo 组元数据（契约 JSON）

consumer 启动：
  @DubboReference → 查 Nacos mapping：接口名 → 应用名
                  → 订阅服务列表：应用名 → 实例 IP:端口（长连接推送，动态感知上下线）
                  → 拉 dubbo 组元数据校验契约 → 预建 TCP 长连接 → 生成代理注入

一次调用：
  代理拦截方法调用 → 序列化参数（hessian2/fastjson2，DTO 必须 Serializable）
  → 按负载均衡挑实例 → TCP 发送 → provider 反序列化、反射调用实现类
  → 结果序列化回传 → consumer 反序列化，像本地方法一样拿到 List<ItemDTO>
```

Nacos 三个存储位置各司其职：

| 位置 | 内容 | 回答的问题 |
|---|---|---|
| 服务列表（服务管理） | 应用名 + 实例 IP:端口，心跳实时维护 | 这个人**在哪** |
| 配置 Group=`mapping` | 接口全限定名 → 应用名 | 这个接口**由谁提供** |
| 配置 Group=`dubbo` | 方法签名/序列化/绑定端口的契约 JSON | 这个服务**长什么样** |

`mapping` + `dubbo` 两个 Group 是 Dubbo 3 应用级注册的产物：注册中心只存应用（抗规模、对齐 K8s），接口到应用的翻译交给 mapping，契约细节降级到配置中心。

### 2.3 接口多实现的处置

| 场景 | 方案 |
|---|---|
| 同进程内多策略 | 一个 `@DubboService` 入口 + 内部策略模式（`Map<类型, 实现>` 注入分发） |
| 不同进程多实现并行 | provider 用 `@DubboService(version=…, group=…)` 区分，consumer `@DubboReference` 对齐 |
| 灰度/按流量切 | 标签路由、条件路由，代码零改动 |
| 运行时动态决定调谁 | 泛化调用 `GenericService`，或多 reference 并行比价 |

### 2.4 一个接口连不上时的排查顺序

1. Nacos 服务列表有没有 `providers:接口全限定名`？（没有 → provider 没起来/没注册）
2. 包名版本两端是否一致？（接口搬过包，provider 还是旧 jar → 见 §4 常见坑）
3. gateway 日志搜 `Referred dubbo service` / `invokers: 0`。

## 3. Maven 多模块：坐标、仓库与 reactor

### 3.1 坐标 → jar 的解析顺序

```
com.rs:recall-api:1.0.0
① reactor（本次构建工程内的模块）→ 直接用 target/ 产物，不经过仓库
② 本地仓库 ~/.m2/repository/com/rs/recall-api/1.0.0/*.jar
③ 远程仓库（中央 / Nexus 私服）→ 下载后缓存到本地
```

- 同工程（同 parent）：根目录整体构建走 reactor，产物**不落仓库**也能互相引用；
- 跨工程：必须先 `mvn install`（进本地仓库）或 `mvn deploy`（发私服），consumer 才能解析到。
- 本地仓库目录里出现 `*.lastUpdated` 文件 = 上次远程查找失败的标记，jar 本体不存在。

### 3.2 reactor（反应堆）

多模块构建的依赖图调度器：收集全部模块依赖 → 拓扑排序（被依赖者先构建）→ 下游解析坐标时直接对接上游刚产出的 target。常用组合：

```bash
mvn package                          # 全模块按依赖序构建
mvn package -pl gateway-service -am  # 只构建 gateway 及其依赖链（-am = also-make）
cd 某子模块 && mvn package            # 脱离 reactor，依赖只能靠仓库 → 容易 "Could not find artifact"
```

### 3.3 改坐标/包名的影响面

| 改动 | 需要同步的位置 |
|---|---|
| artifactId / version | 所有消费方的 pom.xml |
| 接口包名/类名 | 所有消费方的 import、provider 的 implements、注册中心服务名（需重新部署） |

推荐：api 依赖的版本由根 pom `dependencyManagement` 统一管理（`${project.version}`），消费方只写 groupId:artifactId 不写 version。

## 4. 本项目踩过的坑（运维备忘）

1. **只 build 不 package，容器跑旧代码**：Dockerfile 是 `COPY xxx/target/*.jar`。改完代码必须 `mvn package` 后再 `docker compose up -d --build`，否则镜像打进去的还是旧 jar（症状：Nacos 注册的还是旧接口名）。
2. **重建 gateway 后前端 502**：frontend 的 Nginx 容器缓存了 gateway 旧容器 IP（DNS 解析结果不随容器重建刷新），`docker restart rs-frontend` 即可。
3. **Nacos 残留旧条目**：provider 的服务注册是临时实例，容器停掉后 30s 内自动注销；但配置中心 `mapping`/`dubbo` 组的元数据不会自动删，接口改名后需手动清理。
4. **本机 JDK 26 + Mockito**：byte-buddy 版本范围不认识 JDK 26，mock 类报错。根 pom surefire 已固定 `-Dnet.bytebuddy.experimental=true`。

## 5. 测试

- 各模块 `spring-boot-starter-test` + JUnit5 + Mockito，**不依赖 MySQL/Redis/Kafka 实例**（全部 mock），`mvn test` 任意机器可跑。
- 覆盖：粗排排序/去重、重排打散/回填、召回多路合并、精排打分、网关各 controller、行为消费热度增量（click+3/like+5/expose+0.5）、爬虫文本清洗。
- api 模块只有接口声明，不建空壳测试。

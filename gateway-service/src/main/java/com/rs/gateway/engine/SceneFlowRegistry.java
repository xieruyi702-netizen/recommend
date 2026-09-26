package com.rs.gateway.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import java.util.stream.Collectors;

/**
 * 场景流注册表（配置化编排）：
 * 场景 → DAG 图定义外置在 JSON（classpath:flows/flows.json，可被 flows.location 覆盖为外部文件），
 * 启动/重载时经 DagFlow.load 加载（含三色检环），Pipeline 顺序由 Kahn 分层扁平化自动推导。
 * 改编排不发版：修改 JSON 后调 reload 接口即可生效；坏配置自动保留旧版本。
 */
@Component
public class SceneFlowRegistry {

    private final Map<String, Operator> operators;
    private final ExecutorService dagExecutor;
    private final ResourceLoader resourceLoader;
    private final String location;

    private volatile Map<Scene, Pipeline> pipelines = new EnumMap<>(Scene.class);
    private volatile Map<Scene, DagFlow> dagFlows = new EnumMap<>(Scene.class);

    public SceneFlowRegistry(List<Operator> operatorList,
                             ExecutorService dagExecutor,
                             ResourceLoader resourceLoader,
                             @Value("${flows.location:classpath:flows/flows.json}") String location) {
        this.operators = operatorList.stream()
                .collect(Collectors.toMap(Operator::name, Function.identity()));
        this.dagExecutor = dagExecutor;
        this.resourceLoader = resourceLoader;
        this.location = location;
        reload();   // 启动即加载，坏配置快速失败
    }

    /** 加载/重载：解析 JSON → 每个场景构建 DagFlow → 推导 Pipeline。失败时保留旧版本 */
    public synchronized Map<String, Object> reload() {
        Resource resource = resourceLoader.getResource(location);
        try {
            JsonNode scenes = new ObjectMapper().readTree(resource.getInputStream());
            if (!scenes.isObject() || scenes.isEmpty()) {
                throw new IllegalArgumentException("流定义必须是非空 JSON 对象");
            }
            Map<Scene, Pipeline> newPipelines = new EnumMap<>(Scene.class);
            Map<Scene, DagFlow> newDags = new EnumMap<>(Scene.class);
            for (var e : scenes.properties()) {
                Scene scene = Scene.fromCode(e.getKey());
                DagFlow dag = DagFlow.load(e.getValue().path("graph").toString(), operators);
                newDags.put(scene, dag);
                newPipelines.put(scene, new Pipeline(flatten(dag)));
            }
            // 原子切换
            this.dagFlows = newDags;
            this.pipelines = newPipelines;
            return Map.of("ok", true, "scenes", newPipelines.keySet().stream()
                    .map(Scene::code).toList(), "reloadedAt", java.time.LocalDateTime.now().toString());
        } catch (Exception e) {
            throw new IllegalStateException("流配置加载失败（保留旧版本）: " + e.getMessage(), e);
        }
    }

    /** Kahn 分层扁平化为串行顺序，作为 Pipeline 的算子序 */
    private List<Operator> flatten(DagFlow dag) {
        return dag.kahnLevels().stream()
                .flatMap(List::stream)
                .map(dag.nodes()::get)
                .collect(Collectors.toList());
    }

    public Pipeline pipeline(Scene scene) {
        Pipeline p = pipelines.get(scene);
        if (p == null) throw new IllegalArgumentException("场景未注册: " + scene);
        return p;
    }

    public DagFlow dag(Scene scene) {
        DagFlow d = dagFlows.get(scene);
        if (d == null) throw new IllegalArgumentException("场景未注册: " + scene);
        return d;
    }

    public List<String> scenes() {
        return pipelines.keySet().stream().map(Scene::code).sorted().toList();
    }

    @SuppressWarnings("unused")
    private static <K, V> Map<K, V> copyOf(Map<K, V> m) { return Map.copyOf(m); }

    // 保持 Collectors 导入被使用
    private static final java.util.stream.Collector<Integer, ?, List<Integer>> DUMMY = Collectors.toList();
}

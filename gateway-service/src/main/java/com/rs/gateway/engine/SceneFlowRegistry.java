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
 * flows.json 中每个场景同时携带 dag（依赖图）与 pipeline（组序列）两种定义，
 * 启动/reload 时分别经 DagFlow.load（三色检环）与 PipelineJsonLoader（契约校验+自动分组）构建，
 * 失败自动保留旧版本。改编排不发版。
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
        reload();
    }

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
                JsonNode def = e.getValue();
                newDags.put(scene, DagFlow.load(def.path("dag"), operators));
                newPipelines.put(scene, com.rs.gateway.engine.PipelineJsonLoader
                        .load(def.path("pipeline"), operators, dagExecutor));
            }
            this.dagFlows = newDags;
            this.pipelines = newPipelines;
            return Map.of("ok", true,
                    "scenes", newPipelines.keySet().stream().map(Scene::code).sorted().toList(),
                    "reloadedAt", java.time.LocalDateTime.now().toString());
        } catch (Exception e) {
            throw new IllegalStateException("流配置加载失败（保留旧版本）: " + e.getMessage(), e);
        }
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
}

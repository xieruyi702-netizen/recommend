package com.rs.gateway.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/** DAG 定义：节点（算子）+ 依赖边；支持 JSON 加载、三色标记法环检测、Kahn 分层拓扑 */
public class DagFlow {

    private final Map<String, Operator> nodes = new LinkedHashMap<>();
    private final Map<String, Set<String>> dependencies = new LinkedHashMap<>();

    public DagFlow node(String name, Operator operator) {
        nodes.put(name, operator);
        dependencies.putIfAbsent(name, new LinkedHashSet<>());
        return this;
    }

    public DagFlow dependsOn(String node, String... dependsOn) {
        dependencies.get(node).addAll(Arrays.asList(dependsOn));
        return this;
    }

    public Map<String, Operator> nodes() { return nodes; }

    public Map<String, Set<String>> dependencies() { return dependencies; }

    /**
     * 图加载：从 JSON 定义构建 DAG，算子从注册表按名匹配。
     * JSON 形如 [{"name":"recall","dependsOn":[]},{"name":"rank","dependsOn":["coarseRank"]}]
     */
    public static DagFlow load(String json, Map<String, Operator> registry) throws Exception {
        return load(new ObjectMapper().readTree(json), registry);
    }

    public static DagFlow load(JsonNode arr, Map<String, Operator> registry) {
        DagFlow flow = new DagFlow();
        if (!arr.isArray() || arr.isEmpty()) throw new IllegalArgumentException("DAG 定义为空");
        for (JsonNode n : arr) {
            String name = n.path("name").asText("");
            Operator op = registry.get(name);
            if (op == null) throw new IllegalArgumentException("未注册的算子: " + name);
            flow.node(name, op);
            for (JsonNode dep : n.path("dependsOn")) {
                flow.dependencies.get(name).add(dep.asText());
            }
        }

        // 数据契约校验：特征的生产者必须唯一
        Map<String, String> producerOf = new LinkedHashMap<>();
        for (var e : flow.nodes().entrySet()) {
            for (String feature : e.getValue().exports()) {
                String prev = producerOf.putIfAbsent(feature, e.getKey());
                if (prev != null) {
                    throw new IllegalArgumentException("特征[" + feature + "]被[" + prev + "]与[" + e.getKey()
                            + "]重复产出，请用不同 version/group 区分或重命名特征");
                }
            }
        }

        // 依赖边自动推导：算子 import 的特征，由产出该特征的算子提供依赖边
        for (var e : flow.nodes().entrySet()) {
            for (String feature : e.getValue().imports()) {
                String producer = producerOf.get(feature);
                if (producer == null) {
                    throw new IllegalArgumentException("算子[" + e.getKey() + "] import 的特征[" + feature
                            + "]没有任何节点产出");
                }
                if (!producer.equals(e.getKey()) && flow.nodes().containsKey(producer)) {
                    flow.dependencies.get(e.getKey()).add(producer);
                }
            }
        }

        // 显式 dependsOn 的节点也必须存在
        for (var e : flow.dependencies.entrySet()) {
            for (String dep : e.getValue()) {
                if (!flow.nodes.containsKey(dep)) {
                    throw new IllegalArgumentException("依赖的节点不存在: " + dep);
                }
            }
        }
        flow.validate();   // 三色标记法检环，坏图尽早失败
        return flow;
    }

    /**
     * 三色标记法 DFS 环检测：WHITE 未访问 / GRAY 递归栈中（再次遇到=有环）/ BLACK 完成。
     * 返回 this；发现环抛出异常并携带环路径。
     */
    public DagFlow validate() {
        final int WHITE = 0, GRAY = 1, BLACK = 2;
        Map<String, Integer> color = new LinkedHashMap<>();
        for (String n : nodes.keySet()) color.put(n, WHITE);
        Map<String, String> parent = new LinkedHashMap<>();
        Deque<String> stack = new LinkedList<>();

        for (String start : nodes.keySet()) {
            if (color.get(start) != WHITE) continue;
            stack.push(start);
            color.put(start, GRAY);
            while (!stack.isEmpty()) {
                String cur = stack.peek();
                String next = null;
                for (String dep : dependencies.getOrDefault(cur, Set.of())) {
                    if (color.get(dep) == GRAY) {
                        throw new IllegalStateException("DAG 存在环: " + cyclePath(dep, parent));
                    }
                    if (color.get(dep) == WHITE) { next = dep; break; }
                }
                if (next != null) {
                    color.put(next, GRAY);
                    parent.put(next, cur);
                    stack.push(next);
                } else {
                    color.put(cur, BLACK);
                    stack.pop();
                }
            }
        }
        return this;
    }

    /** 回溯拼接环路径，方便定位 */
    private String cyclePath(String from, Map<String, String> parent) {
        List<String> path = new ArrayList<>();
        String cur = from;
        while (cur != null && path.size() <= nodes.size()) {
            path.add(cur);
            cur = parent.get(cur);
        }
        path.add(from);
        return String.join(" -> ", path);
    }

    /**
     * Kahn 算法分层拓扑：入度归零的节点进入当前层，同层节点可并行。
     * 若存在环，已处理节点数 < 总节点数，抛出异常（配合 validate 双保险）。
     */
    public List<List<String>> kahnLevels() {
        Map<String, Set<String>> incoming = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String n : nodes.keySet()) {
            incoming.putIfAbsent(n, new HashSet<>());
            inDegree.putIfAbsent(n, 0);
        }
        for (var e : dependencies.entrySet()) {
            for (String dep : e.getValue()) {
                // dep 指向 e.getKey()：dep 完成 → key 入度减一
                if (incoming.get(e.getKey()).add(dep)) {
                    inDegree.merge(e.getKey(), 1, Integer::sum);
                }
            }
        }

        List<List<String>> levels = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (var e : inDegree.entrySet()) {
            if (e.getValue() == 0) current.add(e.getKey());
        }
        int processed = 0;
        while (!current.isEmpty()) {
            levels.add(current);
            processed += current.size();
            List<String> next = new ArrayList<>();
            for (String node : current) {
                for (String downstream : nodes.keySet()) {
                    if (incoming.get(downstream).remove(node)) {
                        inDegree.merge(downstream, -1, Integer::sum);
                        if (inDegree.get(downstream) == 0) next.add(downstream);
                    }
                }
            }
            current = next;
        }
        if (processed != nodes.size()) {
            throw new IllegalStateException("DAG 存在环，无法完成拓扑排序");
        }
        return levels;
    }
}

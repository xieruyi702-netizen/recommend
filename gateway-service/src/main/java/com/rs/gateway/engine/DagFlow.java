package com.rs.gateway.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** DAG 定义：节点（算子）+ 依赖边。构建期只描述结构，执行交给 DagExecutor */
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

    /** Kahn 分层：依赖同层无环的前提下，把节点按依赖深度分成可并行的层 */
    public List<List<String>> levels() {
        Map<String, Integer> depth = new LinkedHashMap<>();
        List<List<String>> levels = new ArrayList<>();
        int maxDepth = 0;
        for (String name : nodes.keySet()) depth.put(name, 0);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var e : dependencies.entrySet()) {
                for (String dep : e.getValue()) {
                    int d = depth.get(e.getKey());
                    if (depth.get(dep) >= d) {
                        depth.put(e.getKey(), depth.get(dep) + 1);
                        maxDepth = Math.max(maxDepth, depth.get(e.getKey()));
                        changed = true;
                    }
                }
            }
        }
        for (int i = 0; i <= maxDepth; i++) levels.add(new ArrayList<>());
        depth.forEach((name, d) -> levels.get(d).add(name));
        levels.removeIf(List::isEmpty);
        return levels;
    }
}

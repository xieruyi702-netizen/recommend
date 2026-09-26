package com.rs.gateway.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Pipeline JSON 加载器：顺序 + 串并行标志 + import/export 契约 → 自动分组校验。
 *
 * 三遍处理：
 *   ① 契约校验：每个 import 必须有且仅有一个"更早阶段"的 export 生产者（缺生产/重复产出/顺序颠倒均报错）
 *   ② 意愿分组：相邻的 parallel 算子合并为候选并行组，serial 算子自成阶段
 *   ③ 依赖修正：候选组内若存在相互依赖（组内成员的 import 来自组内成员的 export）则拆组降级为串行
 */
public final class PipelineJsonLoader {

    private PipelineJsonLoader() {
    }

    private record Stage(String name, String mode, List<String> imports, List<String> exports, Operator op) {
    }

    public static Pipeline load(JsonNode stagesNode, Map<String, Operator> registry,
                                ExecutorService pool) throws Exception {
        List<Stage> stages = parse(stagesNode, registry);
        validateContracts(stages);
        return assemble(stages, pool);
    }

    // ── ① 解析与契约校验 ──
    private static List<Stage> parse(JsonNode stagesNode, Map<String, Operator> registry) {
        if (!stagesNode.isArray() || stagesNode.isEmpty()) {
            throw new IllegalArgumentException("pipeline 定义必须是非空数组");
        }
        List<Stage> stages = new ArrayList<>();
        Map<String, Integer> producerIdx = new HashMap<>();
        for (int i = 0; i < stagesNode.size(); i++) {
            JsonNode n = stagesNode.get(i);
            String name = n.path("name").asText("");
            Operator op = registry.get(name);
            if (op == null) throw new IllegalArgumentException("未注册的算子: " + name);
            String mode = n.path("mode").asText("serial");
            if (!mode.equals("serial") && !mode.equals("parallel")) {
                throw new IllegalArgumentException("算子[" + name + "] mode 非法: " + mode);
            }
            List<String> imports = toStringList(n.path("imports"));
            List<String> exports = toStringList(n.path("exports"));
            for (String exp : exports) {
                Integer prev = producerIdx.putIfAbsent(exp, i);
                if (prev != null) {
                    throw new IllegalArgumentException("特征[" + exp + "]被算子[" + name + "]与第" + prev + "阶段重复产出");
                }
                producerIdx.put("exported@" + i + ":" + exp, i);   // 占位，防止同阶段重复
            }
            stages.add(new Stage(name, mode, imports, exports, op));
        }
        return stages;
    }

    /** import 校验：每个消费的特征必须有且仅有一个更早阶段的生产者 */
    private static void validateContracts(List<Stage> stages) {
        for (int i = 0; i < stages.size(); i++) {
            for (String imp : stages.get(i).imports()) {
                Integer p = null;
                for (int j = 0; j < stages.size() && p == null; j++) {
                    if (stages.get(j).exports().contains(imp)) p = j;
                }
                if (p == null) throw new IllegalArgumentException("算子[" + stages.get(i).name()
                        + "] import 的特征[" + imp + "]没有任何生产者");
                if (p >= i) throw new IllegalArgumentException("算子[" + stages.get(i).name()
                        + "] import 的特征[" + imp + "]在其消费之后才产出（生产者阶段 " + p + "）");
            }
        }
    }

    // ── ②③ 意愿分组 + 依赖修正 ──
    private static Pipeline assemble(List<Stage> stages, ExecutorService pool) {
        List<Operator> out = new ArrayList<>();
        int i = 0;
        while (i < stages.size()) {
            Stage first = stages.get(i);
            if (!first.mode().equals("parallel")) {
                out.add(first.op());
                i++;
                continue;
            }
            // 尝试把后续相邻的 parallel 算子并入组；组内出现相互依赖则拆组
            List<Stage> group = new ArrayList<>(List.of(first));
            int j = i + 1;
            while (j < stages.size() && stages.get(j).mode().equals("parallel")
                    && compatible(group, stages.get(j))) {
                group.add(stages.get(j));
                j++;
            }
            out.add(new ParallelGroup(groupName(group), pool,
                    group.stream().map(Stage::op).toArray(Operator[]::new)));
            i = j;
        }
        return new Pipeline(out);
    }

    /** 候选算子与组内任一成员存在数据依赖（任一方向）则不可同组 */
    private static boolean compatible(List<Stage> group, Stage candidate) {
        var groupExports = group.stream().flatMap(s -> s.exports().stream()).toList();
        var groupImports = group.stream().flatMap(s -> s.imports().stream()).toList();
        boolean candidateDependsOnGroup = candidate.imports().stream().anyMatch(groupExports::contains);
        boolean groupDependsOnCandidate = candidate.exports().stream().anyMatch(groupImports::contains);
        return !candidateDependsOnGroup && !groupDependsOnCandidate;
    }

    private static String groupName(List<Stage> group) {
        return String.join("+", group.stream().map(Stage::name).toList());
    }

    private static List<String> toStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) node.forEach(n -> list.add(n.asText()));
        return list;
    }
}

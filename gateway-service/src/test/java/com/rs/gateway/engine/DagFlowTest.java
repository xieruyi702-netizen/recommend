package com.rs.gateway.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DagFlowTest {

    private DagFlow valid() {
        return new DagFlow()
                .node("recall", op("recall"))
                .node("profile", op("profile"))
                .node("coarse", op("coarse")).dependsOn("coarse", "recall", "profile")
                .node("rank", op("rank")).dependsOn("rank", "coarse");
    }

    private Operator op(String name) {
        return new Operator() {
            @Override public String name() { return name; }
            @Override public void execute(FlowContext ctx) { }
        };
    }

    @Test
    void kahnLevelsShouldGroupIndependentNodes() {
        List<List<String>> levels = valid().kahnLevels();

        assertEquals(3, levels.size());
        // 第 0 层：无依赖的 recall、profile（同层可并行）
        assertTrue(levels.get(0).containsAll(List.of("recall", "profile")));
        assertEquals(List.of("coarse"), levels.get(1));
        assertEquals(List.of("rank"), levels.get(2));
    }

    @Test
    void cycleShouldBeDetectedByThreeColorDfs() {
        DagFlow cyclic = new DagFlow()
                .node("a", op("a")).dependsOn("a", "b")
                .node("b", op("b")).dependsOn("b", "a");   // a→b→a 互依赖

        var e = assertThrows(IllegalStateException.class, cyclic::validate);
        assertTrue(e.getMessage().contains("环"));
    }

    @Test
    void kahnShouldAlsoFailOnCycle() {
        DagFlow cyclic = new DagFlow()
                .node("a", op("a")).dependsOn("a", "b")
                .node("b", op("b")).dependsOn("b", "a");   // 互依赖

        assertThrows(IllegalStateException.class, cyclic::kahnLevels);
    }

    @Test
    void loadShouldDeriveEdgesFromImportsAndExports() throws Exception {
        String json = """
                [
                  {"name": "recall",  "imports": [],                 "exports": ["candidates"]},
                  {"name": "coarse",  "imports": ["candidates"],     "exports": ["coarsed"]}
                ]
                """;
        Map<String, Operator> registry = Map.of(
                "recall", op("recall"), "coarse", op("coarse"));

        DagFlow flow = DagFlow.load(json, registry);

        // 依赖边由 imports/exports 自动推导：coarse 依赖 recall
        assertEquals(List.of("recall"), flow.kahnLevels().get(0));
        assertEquals(List.of("coarse"), flow.kahnLevels().get(1));

        // 消费的特征无人产出 → 明确报错
        var e = assertThrows(IllegalArgumentException.class,
                () -> DagFlow.load(json, Map.of("coarse", op("coarse"))));
        assertTrue(e.getMessage().contains("candidates"));
    }

    @Test
    void duplicateProducerShouldBeRejected() {
        DagFlow dup = new DagFlow()
                .node("a", op("a"))
                .node("b", op("b"));

        // 两个节点产出同一特征（模拟：通过 validate 前的 exports 契约检查）
        assertThrows(IllegalArgumentException.class, () ->
                DagFlow.load("[{\"name\":\"x\",\"imports\":[\"f\"]},{\"name\":\"y\",\"imports\":[\"f\"]}]",
                        Map.of("x", new Operator() {
                            @Override public String name() { return "x"; }
                            @Override public void execute(FlowContext ctx) { }
                            @Override public java.util.Set<String> exports() { return java.util.Set.of("f"); }
                        },
                           "y", new Operator() {
                            @Override public String name() { return "y"; }
                            @Override public void execute(FlowContext ctx) { }
                            @Override public java.util.Set<String> exports() { return java.util.Set.of("f"); }
                        })));
    }
}

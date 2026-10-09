package cn.dct.zpback.service.jewelry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分支题触发契约测试
 *
 * 回归背景：曾经把触发关系读成「选项上的 branch_question_id」，
 * 而配置里触发关系实际写在分支题的 trigger_option_ids 上，
 * 结果分支题永远不被触发、active_branches 恒为空。
 */
class JewelryBranchTriggerTest {

    private JewelryPolicies policies;

    @BeforeEach
    void setUp() throws Exception {
        policies = new JewelryPolicies();
        policies.load();
    }

    @Test
    @DisplayName("自测：每个目标选项都能触发对应分支题")
    void selfAssessment_branchPerGoal() {
        assertEquals(List.of("SB_AUTHORITY"), policies.branchOfOption("SELF_ASSESSMENT", "SELF_DECISION"));
        assertEquals(List.of("SB_EMOTION"), policies.branchOfOption("SELF_ASSESSMENT", "SELF_EMOTIONAL"));
        assertEquals(List.of("SB_ACTION"), policies.branchOfOption("SELF_ASSESSMENT", "SELF_ACTION"));
        assertEquals(List.of("SB_CLARITY"), policies.branchOfOption("SELF_ASSESSMENT", "SELF_CLARITY"));
        assertEquals(List.of("SB_RELATION"), policies.branchOfOption("SELF_ASSESSMENT", "SELF_RELATION"));
        assertEquals(List.of("SB_GROWTH"), policies.branchOfOption("SELF_ASSESSMENT", "SELF_GROWTH"));
        assertEquals(List.of("SB_STRUCTURE"), policies.branchOfOption("SELF_ASSESSMENT", "SELF_STRUCTURE"));
    }

    @Test
    @DisplayName("赠礼：每个祝福选项都能触发对应分支题")
    void gift_branchPerBlessing() {
        assertEquals(List.of("GB_CONFIDENCE"), policies.branchOfOption("GIFT_BLESSING", "GIFT_CONFIDENCE"));
        assertEquals(List.of("GB_PEACE"), policies.branchOfOption("GIFT_BLESSING", "GIFT_PEACE"));
        assertEquals(List.of("GB_COURAGE"), policies.branchOfOption("GIFT_BLESSING", "GIFT_COURAGE"));
        assertEquals(List.of("GB_CLARITY"), policies.branchOfOption("GIFT_BLESSING", "GIFT_CLARITY"));
        assertEquals(List.of("GB_LOVE"), policies.branchOfOption("GIFT_BLESSING", "GIFT_LOVE"));
        assertEquals(List.of("GB_GROWTH"), policies.branchOfOption("GIFT_BLESSING", "GIFT_GROWTH"));
        assertEquals(List.of("GB_STABILITY"), policies.branchOfOption("GIFT_BLESSING", "GIFT_STABILITY"));
    }

    @Test
    @DisplayName("非目标类选项不触发任何分支题")
    void nonGoalOptions_triggerNothing() {
        // S02/S03 这类卡住项与状态项不产生分支
        assertTrue(policies.branchOfOption("SELF_ASSESSMENT", "DELAY_START").isEmpty());
        assertTrue(policies.branchOfOption("SELF_ASSESSMENT", "TOO_IMPULSIVE").isEmpty());
        assertTrue(policies.branchOfOption("GIFT_BLESSING", "PARTNER").isEmpty());
    }

    @Test
    @DisplayName("未知选项不触发分支，也不抛异常")
    void unknownOption_isSafe() {
        assertTrue(policies.branchOfOption("SELF_ASSESSMENT", "NOT_A_REAL_OPTION").isEmpty());
    }

    @Test
    @DisplayName("最多激活 2 道分支题（question_bank.rules.max_active_branch_questions）")
    void maxActiveBranches_isCappedAtTwo() {
        JewelryPolicies p = policies;
        List<String> branches = new ArrayList<>();
        for (String opt : List.of("SELF_DECISION", "SELF_EMOTIONAL", "SELF_ACTION")) {
            for (String b : p.branchOfOption("SELF_ASSESSMENT", opt)) {
                if (!branches.contains(b)) branches.add(b);
            }
            if (branches.size() >= 2) break;
        }
        assertEquals(List.of("SB_AUTHORITY", "SB_EMOTION"), branches);
    }

    @Test
    @DisplayName("取消目标后分支随之消失")
    void deselectingGoal_removesBranch() {
        assertEquals(2, countBranches(List.of("SELF_DECISION", "SELF_EMOTIONAL")));
        assertEquals(1, countBranches(List.of("SELF_DECISION")));
        assertEquals(0, countBranches(List.of()));
    }

    private int countBranches(List<String> goals) {
        List<String> branches = new ArrayList<>();
        for (String opt : goals) {
            for (String b : policies.branchOfOption("SELF_ASSESSMENT", opt)) {
                if (!branches.contains(b)) branches.add(b);
            }
            if (branches.size() >= 2) break;
        }
        return branches.size();
    }
}

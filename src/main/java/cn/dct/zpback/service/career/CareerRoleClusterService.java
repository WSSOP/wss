package cn.dct.zpback.service.career;

import cn.dct.zpback.entity.career.CareerExample;
import cn.dct.zpback.entity.career.CareerRoleCluster;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 事业报告角色集群服务
 */
@Service
public class CareerRoleClusterService {

    private final Map<String, CareerRoleCluster> roleClusters = new HashMap<>();

    public CareerRoleClusterService() {
        initRoleClusters();
    }

    /**
     * 初始化所有角色集群
     */
    private void initRoleClusters() {
        // RC01 - 研究分析型
        CareerRoleCluster rc01 = new CareerRoleCluster();
        rc01.setRoleClusterId("RC01_RESEARCH_ANALYTICAL");
        rc01.setDisplayName("研究分析型");
        rc01.setCoreTags(Arrays.asList("ANALYSIS", "RESEARCH", "INFORMATION", "DISCERNMENT", "ROOT_CAUSE", "STRATEGIC_JUDGMENT", "COMPLEXITY", "PRECISION"));
        rc01.setSupportingTags(Arrays.asList("SYSTEM_ANALYSIS", "DIAGNOSIS", "RISK_HANDLING"));
        rc01.setMinimumClaimStrength("MODERATE");
        rc01.setExamples(Arrays.asList(
                createExample("EXP_01_01", "RC01_RESEARCH_ANALYTICAL", "行业研究", Arrays.asList("ANALYSIS"), Arrays.asList("INFORMATION", "RESEARCH")),
                createExample("EXP_01_02", "RC01_RESEARCH_ANALYTICAL", "商业分析", Arrays.asList("ANALYSIS"), Arrays.asList("INFORMATION", "STRATEGIC_JUDGMENT")),
                createExample("EXP_01_03", "RC01_RESEARCH_ANALYTICAL", "数据分析", Arrays.asList("ANALYSIS"), Arrays.asList("INFORMATION", "PRECISION", "SYSTEM_ANALYSIS")),
                createExample("EXP_01_04", "RC01_RESEARCH_ANALYTICAL", "战略研究", Arrays.asList("ANALYSIS"), Arrays.asList("STRATEGIC_JUDGMENT", "RESEARCH")),
                createExample("EXP_01_05", "RC01_RESEARCH_ANALYTICAL", "用户研究", Arrays.asList("ANALYSIS"), Arrays.asList("RESEARCH", "PEOPLE_RESPONSE")),
                createExample("EXP_01_06", "RC01_RESEARCH_ANALYTICAL", "风险分析", Arrays.asList("ANALYSIS"), Arrays.asList("COMPLEXITY", "RISK_HANDLING", "DIAGNOSIS"))
        ));
        roleClusters.put(rc01.getRoleClusterId(), rc01);

        // RC02 - 专业顾问型
        CareerRoleCluster rc02 = new CareerRoleCluster();
        rc02.setRoleClusterId("RC02_ADVISORY_PROFESSIONAL");
        rc02.setDisplayName("专业顾问型");
        rc02.setCoreTags(Arrays.asList("ADVISORY", "JUDGMENT", "KNOWLEDGE", "PROFESSIONAL_AUTHORITY", "RULE_BASED", "CLIENT", "GUIDANCE"));
        rc02.setSupportingTags(Arrays.asList("ANALYSIS", "TEACHING", "MENTORING"));
        rc02.setMinimumClaimStrength("MODERATE");
        rc02.setExamples(Arrays.asList(
                createExample("EXP_02_01", "RC02_ADVISORY_PROFESSIONAL", "专业咨询", Arrays.asList("ADVISORY"), Arrays.asList("JUDGMENT", "CLIENT")),
                createExample("EXP_02_02", "RC02_ADVISORY_PROFESSIONAL", "战略顾问", Arrays.asList("ADVISORY"), Arrays.asList("STRATEGIC_JUDGMENT", "CLIENT")),
                createExample("EXP_02_03", "RC02_ADVISORY_PROFESSIONAL", "法律与合规", Arrays.asList("ADVISORY", "RULE_BASED"), Arrays.asList("ANALYSIS", "JUDGMENT", "CLIENT")),
                createExample("EXP_02_04", "RC02_ADVISORY_PROFESSIONAL", "财税专业服务", Arrays.asList("ADVISORY", "RULE_BASED"), Arrays.asList("ANALYSIS", "PRECISION")),
                createExample("EXP_02_05", "RC02_ADVISORY_PROFESSIONAL", "管理咨询", Arrays.asList("ADVISORY"), Arrays.asList("JUDGMENT", "CLIENT", "STRATEGIC_JUDGMENT")),
                createExample("EXP_02_06", "RC02_ADVISORY_PROFESSIONAL", "职业顾问", Arrays.asList("ADVISORY", "GUIDANCE"), Arrays.asList("JUDGMENT", "PEOPLE_RESPONSE"))
        ));
        roleClusters.put(rc02.getRoleClusterId(), rc02);

        // RC03 - 教育知识型
        CareerRoleCluster rc03 = new CareerRoleCluster();
        rc03.setRoleClusterId("RC03_EDUCATION_KNOWLEDGE");
        rc03.setDisplayName("教育知识型");
        rc03.setCoreTags(Arrays.asList("TEACHING", "KNOWLEDGE_TRANSFER", "MENTORING", "GUIDANCE", "LEARNING", "EXPLANATION", "SYSTEMATIZATION"));
        rc03.setSupportingTags(Arrays.asList("KNOWLEDGE", "COMMUNICATION", "CONTENT"));
        rc03.setMinimumClaimStrength("MODERATE");
        rc03.setExamples(Arrays.asList(
                createExample("EXP_03_01", "RC03_EDUCATION_KNOWLEDGE", "教学培训", Arrays.asList("TEACHING"), Arrays.asList("KNOWLEDGE_TRANSFER", "EXPLANATION")),
                createExample("EXP_03_02", "RC03_EDUCATION_KNOWLEDGE", "课程研发", Arrays.asList("TEACHING", "KNOWLEDGE_TRANSFER"), Arrays.asList("SYSTEMATIZATION", "CONTENT")),
                createExample("EXP_03_03", "RC03_EDUCATION_KNOWLEDGE", "知识产品", Arrays.asList("KNOWLEDGE_TRANSFER"), Arrays.asList("CONTENT", "SYSTEMATIZATION")),
                createExample("EXP_03_04", "RC03_EDUCATION_KNOWLEDGE", "专业讲师", Arrays.asList("TEACHING"), Arrays.asList("COMMUNICATION", "KNOWLEDGE_TRANSFER")),
                createExample("EXP_03_05", "RC03_EDUCATION_KNOWLEDGE", "企业培训", Arrays.asList("TEACHING"), Arrays.asList("KNOWLEDGE_TRANSFER", "CLIENT")),
                createExample("EXP_03_06", "RC03_EDUCATION_KNOWLEDGE", "导师型工作", Arrays.asList("MENTORING", "GUIDANCE"), Arrays.asList("KNOWLEDGE_TRANSFER", "PEOPLE_RESPONSE"))
        ));
        roleClusters.put(rc03.getRoleClusterId(), rc03);

        // RC04 - 传播表达型
        CareerRoleCluster rc04 = new CareerRoleCluster();
        rc04.setRoleClusterId("RC04_COMMUNICATION_CONTENT");
        rc04.setDisplayName("传播表达型");
        rc04.setCoreTags(Arrays.asList("COMMUNICATION", "INFORMATION", "CONTENT", "EXPRESSION", "PERSUASION", "KNOWLEDGE_TRANSLATION", "PUBLIC_COMMUNICATION"));
        rc04.setSupportingTags(Arrays.asList("CREATIVE", "MEDIA", "NETWORK"));
        rc04.setMinimumClaimStrength("MODERATE");
        rc04.setExamples(Arrays.asList(
                createExample("EXP_04_01", "RC04_COMMUNICATION_CONTENT", "内容策划", Arrays.asList("COMMUNICATION", "CONTENT"), Arrays.asList("INFORMATION", "EXPRESSION")),
                createExample("EXP_04_02", "RC04_COMMUNICATION_CONTENT", "编辑", Arrays.asList("CONTENT"), Arrays.asList("COMMUNICATION", "INFORMATION")),
                createExample("EXP_04_03", "RC04_COMMUNICATION_CONTENT", "品牌内容", Arrays.asList("CONTENT", "COMMUNICATION"), Arrays.asList("EXPRESSION", "PERSUASION")),
                createExample("EXP_04_04", "RC04_COMMUNICATION_CONTENT", "市场传播", Arrays.asList("COMMUNICATION", "PUBLIC_COMMUNICATION"), Arrays.asList("PERSUASION", "INFORMATION")),
                createExample("EXP_04_05", "RC04_COMMUNICATION_CONTENT", "新媒体内容", Arrays.asList("CONTENT", "COMMUNICATION"), Arrays.asList("EXPRESSION", "PUBLIC_COMMUNICATION")),
                createExample("EXP_04_06", "RC04_COMMUNICATION_CONTENT", "公关传播", Arrays.asList("COMMUNICATION", "PUBLIC_COMMUNICATION"), Arrays.asList("PERSUASION", "PEOPLE_RESPONSE"))
        ));
        roleClusters.put(rc04.getRoleClusterId(), rc04);

        // RC05 - 系统运营型
        CareerRoleCluster rc05 = new CareerRoleCluster();
        rc05.setRoleClusterId("RC05_SYSTEM_OPERATIONS");
        rc05.setDisplayName("系统运营型");
        rc05.setCoreTags(Arrays.asList("SYSTEM", "STRUCTURE", "OPERATIONS", "PROCESS", "ORGANIZATION", "LONG_TERM_BUILDING", "COORDINATION", "RELIABILITY"));
        rc05.setSupportingTags(Arrays.asList("PRECISION", "RESPONSIBILITY", "MANAGEMENT"));
        rc05.setMinimumClaimStrength("MODERATE");
        rc05.setExamples(Arrays.asList(
                createExample("EXP_05_01", "RC05_SYSTEM_OPERATIONS", "业务运营", Arrays.asList("OPERATIONS", "SYSTEM"), Arrays.asList("PROCESS", "COORDINATION")),
                createExample("EXP_05_02", "RC05_SYSTEM_OPERATIONS", "流程管理", Arrays.asList("PROCESS", "SYSTEM"), Arrays.asList("ORGANIZATION", "PRECISION")),
                createExample("EXP_05_03", "RC05_SYSTEM_OPERATIONS", "项目运营", Arrays.asList("OPERATIONS", "COORDINATION"), Arrays.asList("PROJECT_OWNERSHIP", "PROCESS")),
                createExample("EXP_05_04", "RC05_SYSTEM_OPERATIONS", "组织运营", Arrays.asList("ORGANIZATION", "OPERATIONS"), Arrays.asList("SYSTEM", "COORDINATION")),
                createExample("EXP_05_05", "RC05_SYSTEM_OPERATIONS", "供应链运营", Arrays.asList("OPERATIONS", "COORDINATION"), Arrays.asList("RESOURCE_COORDINATION", "PROCESS")),
                createExample("EXP_05_06", "RC05_SYSTEM_OPERATIONS", "质量管理", Arrays.asList("PROCESS", "PRECISION"), Arrays.asList("SYSTEM", "RELIABILITY"))
        ));
        roleClusters.put(rc05.getRoleClusterId(), rc05);

        // RC06 - 项目推进型
        CareerRoleCluster rc06 = new CareerRoleCluster();
        rc06.setRoleClusterId("RC06_PROJECT_EXECUTION");
        rc06.setDisplayName("项目推进型");
        rc06.setCoreTags(Arrays.asList("EXECUTION", "PROJECT_OWNERSHIP", "INITIATIVE", "COORDINATION", "GOAL_FOCUS", "DECISION_SPEED", "DELIVERY"));
        rc06.setSupportingTags(Arrays.asList("ACTION", "COMPETITION", "RESULT_ORIENTED"));
        rc06.setMinimumClaimStrength("MODERATE");
        rc06.setExamples(Arrays.asList(
                createExample("EXP_06_01", "RC06_PROJECT_EXECUTION", "项目管理", Arrays.asList("EXECUTION", "PROJECT_OWNERSHIP"), Arrays.asList("COORDINATION", "GOAL_FOCUS")),
                createExample("EXP_06_02", "RC06_PROJECT_EXECUTION", "项目负责人", Arrays.asList("PROJECT_OWNERSHIP", "EXECUTION"), Arrays.asList("DECISION_AUTHORITY", "COORDINATION")),
                createExample("EXP_06_03", "RC06_PROJECT_EXECUTION", "产品项目", Arrays.asList("PROJECT_OWNERSHIP"), Arrays.asList("EXECUTION", "CREATIVE")),
                createExample("EXP_06_04", "RC06_PROJECT_EXECUTION", "交付管理", Arrays.asList("EXECUTION", "DELIVERY"), Arrays.asList("PROCESS", "GOAL_FOCUS")),
                createExample("EXP_06_05", "RC06_PROJECT_EXECUTION", "业务推进", Arrays.asList("INITIATIVE", "EXECUTION"), Arrays.asList("GOAL_FOCUS", "RESULT_ORIENTED"))
        ));
        roleClusters.put(rc06.getRoleClusterId(), rc06);

        // RC07 - 技术解决型
        CareerRoleCluster rc07 = new CareerRoleCluster();
        rc07.setRoleClusterId("RC07_TECHNICAL_PROBLEM_SOLVING");
        rc07.setDisplayName("技术解决型");
        rc07.setCoreTags(Arrays.asList("TECHNICAL", "PROBLEM_SOLVING", "PRECISION", "EXECUTION", "DIAGNOSIS", "REPAIR", "SYSTEM_ANALYSIS", "SPECIALIZATION"));
        rc07.setSupportingTags(Arrays.asList("RESEARCH", "ANALYSIS", "INNOVATION"));
        rc07.setMinimumClaimStrength("MODERATE");
        rc07.setExamples(Arrays.asList(
                createExample("EXP_07_01", "RC07_TECHNICAL_PROBLEM_SOLVING", "技术研发", Arrays.asList("TECHNICAL", "PROBLEM_SOLVING"), Arrays.asList("RESEARCH", "EXECUTION")),
                createExample("EXP_07_02", "RC07_TECHNICAL_PROBLEM_SOLVING", "工程技术", Arrays.asList("TECHNICAL", "EXECUTION"), Arrays.asList("PRECISION", "PROBLEM_SOLVING")),
                createExample("EXP_07_03", "RC07_TECHNICAL_PROBLEM_SOLVING", "系统实施", Arrays.asList("TECHNICAL", "SYSTEM_ANALYSIS"), Arrays.asList("EXECUTION", "PROBLEM_SOLVING")),
                createExample("EXP_07_04", "RC07_TECHNICAL_PROBLEM_SOLVING", "技术支持", Arrays.asList("TECHNICAL", "PROBLEM_SOLVING"), Arrays.asList("DIAGNOSIS", "SERVICE")),
                createExample("EXP_07_05", "RC07_TECHNICAL_PROBLEM_SOLVING", "质量与测试", Arrays.asList("TECHNICAL", "PRECISION"), Arrays.asList("DIAGNOSIS", "SYSTEM_ANALYSIS")),
                createExample("EXP_07_06", "RC07_TECHNICAL_PROBLEM_SOLVING", "解决方案岗位", Arrays.asList("TECHNICAL", "PROBLEM_SOLVING"), Arrays.asList("ANALYSIS", "CLIENT"))
        ));
        roleClusters.put(rc07.getRoleClusterId(), rc07);

        // RC08 - 客户商业型
        CareerRoleCluster rc08 = new CareerRoleCluster();
        rc08.setRoleClusterId("RC08_CLIENT_COMMERCIAL");
        rc08.setDisplayName("客户商业型");
        rc08.setCoreTags(Arrays.asList("CLIENT", "MARKET", "COMMERCIAL", "NEGOTIATION", "TRADE", "VALUE_CREATION", "PERSUASION", "BUSINESS_INTERACTION"));
        rc08.setSupportingTags(Arrays.asList("RELATIONSHIP", "NETWORK", "RESULT_ORIENTED"));
        rc08.setMinimumClaimStrength("MODERATE");
        rc08.setExamples(Arrays.asList(
                createExample("EXP_08_01", "RC08_CLIENT_COMMERCIAL", "商务拓展", Arrays.asList("CLIENT", "COMMERCIAL"), Arrays.asList("NEGOTIATION", "BUSINESS_INTERACTION")),
                createExample("EXP_08_02", "RC08_CLIENT_COMMERCIAL", "客户管理", Arrays.asList("CLIENT", "RELATIONSHIP"), Arrays.asList("BUSINESS_INTERACTION", "VALUE_CREATION")),
                createExample("EXP_08_03", "RC08_CLIENT_COMMERCIAL", "销售顾问", Arrays.asList("CLIENT", "COMMERCIAL"), Arrays.asList("PERSUASION", "NEGOTIATION")),
                createExample("EXP_08_04", "RC08_CLIENT_COMMERCIAL", "商业合作", Arrays.asList("COMMERCIAL", "BUSINESS_INTERACTION"), Arrays.asList("NEGOTIATION", "PARTNERSHIP")),
                createExample("EXP_08_05", "RC08_CLIENT_COMMERCIAL", "客户成功", Arrays.asList("CLIENT", "SERVICE"), Arrays.asList("RELATIONSHIP", "VALUE_CREATION"))
        ));
        roleClusters.put(rc08.getRoleClusterId(), rc08);

        // RC09 - 资源整合型
        CareerRoleCluster rc09 = new CareerRoleCluster();
        rc09.setRoleClusterId("RC09_RESOURCE_INTEGRATION");
        rc09.setDisplayName("资源整合型");
        rc09.setCoreTags(Arrays.asList("RESOURCE_COORDINATION", "NETWORK", "ORGANIZATION", "PARTNERSHIP", "CONNECTION", "CROSS_FUNCTION", "SCALE"));
        rc09.setSupportingTags(Arrays.asList("COORDINATION", "BUSINESS_INTERACTION", "STRATEGIC_JUDGMENT"));
        rc09.setMinimumClaimStrength("MODERATE");
        rc09.setExamples(Arrays.asList(
                createExample("EXP_09_01", "RC09_RESOURCE_INTEGRATION", "商务合作", Arrays.asList("RESOURCE_COORDINATION", "PARTNERSHIP"), Arrays.asList("BUSINESS_INTERACTION", "NETWORK")),
                createExample("EXP_09_02", "RC09_RESOURCE_INTEGRATION", "平台招商", Arrays.asList("RESOURCE_COORDINATION", "NETWORK"), Arrays.asList("COMMERCIAL", "PARTNERSHIP")),
                createExample("EXP_09_03", "RC09_RESOURCE_INTEGRATION", "生态合作", Arrays.asList("PARTNERSHIP", "NETWORK"), Arrays.asList("RESOURCE_COORDINATION", "STRATEGIC_JUDGMENT")),
                createExample("EXP_09_04", "RC09_RESOURCE_INTEGRATION", "渠道管理", Arrays.asList("RESOURCE_COORDINATION", "NETWORK"), Arrays.asList("BUSINESS_INTERACTION", "ORGANIZATION")),
                createExample("EXP_09_05", "RC09_RESOURCE_INTEGRATION", "跨部门协调", Arrays.asList("CROSS_FUNCTION", "COORDINATION"), Arrays.asList("ORGANIZATION", "RESOURCE_COORDINATION"))
        ));
        roleClusters.put(rc09.getRoleClusterId(), rc09);

        // RC10 - 创意价值型
        CareerRoleCluster rc10 = new CareerRoleCluster();
        rc10.setRoleClusterId("RC10_CREATIVE_VALUE");
        rc10.setDisplayName("创意价值型");
        rc10.setCoreTags(Arrays.asList("CREATIVE", "DESIGN", "AESTHETIC", "VALUE_CREATION", "FORM", "CONTENT_CREATION", "EXPERIENCE", "DISTINCTION"));
        rc10.setSupportingTags(Arrays.asList("EXPRESSION", "INNOVATION", "VISION"));
        rc10.setMinimumClaimStrength("MODERATE");
        rc10.setExamples(Arrays.asList(
                createExample("EXP_10_01", "RC10_CREATIVE_VALUE", "视觉设计", Arrays.asList("DESIGN", "AESTHETIC"), Arrays.asList("FORM", "DISTINCTION")),
                createExample("EXP_10_02", "RC10_CREATIVE_VALUE", "品牌创意", Arrays.asList("CREATIVE", "VALUE_CREATION"), Arrays.asList("DISTINCTION", "EXPRESSION")),
                createExample("EXP_10_03", "RC10_CREATIVE_VALUE", "产品设计", Arrays.asList("DESIGN", "CREATIVE"), Arrays.asList("FORM", "VALUE_CREATION")),
                createExample("EXP_10_04", "RC10_CREATIVE_VALUE", "内容创意", Arrays.asList("CONTENT_CREATION", "CREATIVE"), Arrays.asList("EXPRESSION", "DISTINCTION")),
                createExample("EXP_10_05", "RC10_CREATIVE_VALUE", "用户体验", Arrays.asList("DESIGN", "EXPERIENCE"), Arrays.asList("PEOPLE_RESPONSE", "FORM")),
                createExample("EXP_10_06", "RC10_CREATIVE_VALUE", "品牌策划", Arrays.asList("CREATIVE", "VALUE_CREATION"), Arrays.asList("DISTINCTION", "STRATEGIC_JUDGMENT"))
        ));
        roleClusters.put(rc10.getRoleClusterId(), rc10);

        // RC11 - 管理组织型
        CareerRoleCluster rc11 = new CareerRoleCluster();
        rc11.setRoleClusterId("RC11_MANAGEMENT_ORGANIZATION");
        rc11.setDisplayName("管理组织型");
        rc11.setCoreTags(Arrays.asList("DECISION_AUTHORITY", "STRUCTURE_MANAGEMENT", "PEOPLE_MANAGEMENT", "RESOURCE_COORDINATION", "EXECUTION_LEADERSHIP"));
        rc11.setSupportingTags(Arrays.asList("ORGANIZATION", "RESPONSIBILITY", "SYSTEM"));
        rc11.setRequiresManagementResolver(true);
        rc11.setMinimumClaimStrength("MODERATE");
        rc11.setExamples(Arrays.asList(
                createExample("EXP_11_01", "RC11_MANAGEMENT_ORGANIZATION", "项目负责人", Arrays.asList("DECISION_AUTHORITY", "EXECUTION_LEADERSHIP"), Arrays.asList("PROJECT_OWNERSHIP", "COORDINATION")),
                createExample("EXP_11_02", "RC11_MANAGEMENT_ORGANIZATION", "团队负责人", Arrays.asList("PEOPLE_MANAGEMENT", "DECISION_AUTHORITY"), Arrays.asList("EXECUTION_LEADERSHIP", "COORDINATION")),
                createExample("EXP_11_03", "RC11_MANAGEMENT_ORGANIZATION", "职能负责人", Arrays.asList("STRUCTURE_MANAGEMENT", "DECISION_AUTHORITY"), Arrays.asList("RESPONSIBILITY", "ORGANIZATION")),
                createExample("EXP_11_04", "RC11_MANAGEMENT_ORGANIZATION", "业务负责人", Arrays.asList("DECISION_AUTHORITY", "RESOURCE_COORDINATION"), Arrays.asList("EXECUTION_LEADERSHIP", "STRUCTURE_MANAGEMENT")),
                createExample("EXP_11_05", "RC11_MANAGEMENT_ORGANIZATION", "运营负责人", Arrays.asList("STRUCTURE_MANAGEMENT", "OPERATIONS"), Arrays.asList("DECISION_AUTHORITY", "PROCESS"))
        ));
        roleClusters.put(rc11.getRoleClusterId(), rc11);

        // RC12 - 人群支持与服务型
        CareerRoleCluster rc12 = new CareerRoleCluster();
        rc12.setRoleClusterId("RC12_HUMAN_SUPPORT_SERVICE");
        rc12.setDisplayName("人群支持与服务型");
        rc12.setCoreTags(Arrays.asList("PEOPLE_RESPONSE", "SUPPORT", "SERVICE", "CARE", "HUMAN_CONNECTION", "DEVELOPMENT_SUPPORT"));
        rc12.setSupportingTags(Arrays.asList("TEACHING", "COMMUNICATION", "PEOPLE_MANAGEMENT"));
        rc12.setMinimumClaimStrength("MODERATE");
        rc12.setExamples(Arrays.asList(
                createExample("EXP_12_01", "RC12_HUMAN_SUPPORT_SERVICE", "人力资源", Arrays.asList("PEOPLE_RESPONSE", "ORGANIZATION"), Arrays.asList("HUMAN_CONNECTION", "DEVELOPMENT_SUPPORT")),
                createExample("EXP_12_02", "RC12_HUMAN_SUPPORT_SERVICE", "员工发展", Arrays.asList("DEVELOPMENT_SUPPORT", "PEOPLE_RESPONSE"), Arrays.asList("MENTORING", "TEACHING")),
                createExample("EXP_12_03", "RC12_HUMAN_SUPPORT_SERVICE", "客户成功", Arrays.asList("SERVICE", "PEOPLE_RESPONSE"), Arrays.asList("CLIENT", "VALUE_CREATION")),
                createExample("EXP_12_04", "RC12_HUMAN_SUPPORT_SERVICE", "用户服务", Arrays.asList("SERVICE", "PEOPLE_RESPONSE"), Arrays.asList("COMMUNICATION", "SUPPORT")),
                createExample("EXP_12_05", "RC12_HUMAN_SUPPORT_SERVICE", "社群运营", Arrays.asList("PEOPLE_RESPONSE", "HUMAN_CONNECTION"), Arrays.asList("COMMUNICATION", "SERVICE")),
                createExample("EXP_12_06", "RC12_HUMAN_SUPPORT_SERVICE", "培训支持", Arrays.asList("DEVELOPMENT_SUPPORT", "TEACHING"), Arrays.asList("PEOPLE_RESPONSE", "KNOWLEDGE_TRANSFER"))
        ));
        roleClusters.put(rc12.getRoleClusterId(), rc12);
    }

    /**
     * 创建职业示例
     */
    private CareerExample createExample(String id, String roleClusterId, String name, List<String> requiredAll, List<String> requiredAny) {
        CareerExample example = new CareerExample();
        example.setExampleId(id);
        example.setRoleClusterId(roleClusterId);
        example.setDisplayName(name);
        example.setRequiredTagsAll(requiredAll);
        example.setRequiredTagsAny(requiredAny);
        example.setDisplayPriority(1);
        return example;
    }

    /**
     * 获取所有角色集群
     */
    public List<CareerRoleCluster> getAllRoleClusters() {
        return new ArrayList<>(roleClusters.values());
    }

    /**
     * 根据ID获取角色集群
     */
    public CareerRoleCluster getRoleClusterById(String id) {
        return roleClusters.get(id);
    }

    /**
     * 根据标签匹配角色集群
     */
    public List<CareerRoleCluster> matchRoleClusters(List<String> tags) {
        List<CareerRoleCluster> matched = new ArrayList<>();
        for (CareerRoleCluster cluster : roleClusters.values()) {
            int matchCount = 0;
            for (String tag : tags) {
                if (cluster.getCoreTags().contains(tag)) {
                    matchCount += 2;
                }
                if (cluster.getSupportingTags() != null && cluster.getSupportingTags().contains(tag)) {
                    matchCount += 1;
                }
            }
            if (matchCount >= 2) {
                matched.add(cluster);
            }
        }
        return matched;
    }

    /**
     * 根据标签匹配职业示例
     */
    public List<CareerExample> matchCareerExamples(List<String> tags) {
        List<CareerExample> matched = new ArrayList<>();
        for (CareerRoleCluster cluster : roleClusters.values()) {
            for (CareerExample example : cluster.getExamples()) {
                if (isExampleMatch(example, tags)) {
                    matched.add(example);
                }
            }
        }
        return matched;
    }

    /**
     * 检查示例是否匹配标签
     */
    private boolean isExampleMatch(CareerExample example, List<String> tags) {
        // 检查必需标签(全部)
        if (example.getRequiredTagsAll() != null) {
            for (String required : example.getRequiredTagsAll()) {
                if (!tags.contains(required)) {
                    return false;
                }
            }
        }
        // 检查必需标签(任一)
        if (example.getRequiredTagsAny() != null && !example.getRequiredTagsAny().isEmpty()) {
            boolean anyMatch = false;
            for (String required : example.getRequiredTagsAny()) {
                if (tags.contains(required)) {
                    anyMatch = true;
                    break;
                }
            }
            if (!anyMatch) {
                return false;
            }
        }
        // 检查禁止标签
        if (example.getForbiddenTags() != null) {
            for (String forbidden : example.getForbiddenTags()) {
                if (tags.contains(forbidden)) {
                    return false;
                }
            }
        }
        return true;
    }
}

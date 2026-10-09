package cn.dct.zpback.constant;

import java.util.*;

/**
 * 事业深度报告常量定义
 */
public class CareerReportConstants {

    // ============================================================
    // 报告类型和状态
    // ============================================================

    public static final String REPORT_TYPE = "CAREER";
    public static final String SKILL_ID = "CAREER_REPORT_SKILL";
    public static final String SKILL_VERSION = "V1";
    public static final String SCHEMA_VERSION = "CAREER_V1";

    // report_status
    public static final String STATUS_GENERATING = "GENERATING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    // ============================================================
    // Section Keys
    // ============================================================

    public static final String SECTION_CAREER_CORE_FINDINGS = "career_core_findings";
    public static final String SECTION_CAREER_DRIVE_WORK_PATTERN = "career_drive_work_pattern";
    public static final String SECTION_CAREER_STRENGTHS = "career_strengths";
    public static final String SECTION_CAREER_ENVIRONMENT_ROLE = "career_environment_role";
    public static final String SECTION_CAREER_CHALLENGES = "career_challenges";
    public static final String SECTION_CAREER_GROWTH_PATH = "career_growth_path";
    public static final String SECTION_CAREER_INCOME_LINK = "career_income_link";
    public static final String SECTION_CAREER_TIMING = "career_timing";
    public static final String SECTION_CAREER_CAUTIONS = "career_cautions";
    public static final String SECTION_CAREER_RECOMMENDATIONS = "career_recommendations";

    // section_type
    public static final String SECTION_TYPE_OVERVIEW = "OVERVIEW";
    public static final String SECTION_TYPE_DEEP_ANALYSIS = "DEEP_ANALYSIS";
    public static final String SECTION_TYPE_TIMING = "TIMING";
    public static final String SECTION_TYPE_CAUTION = "CAUTION";
    public static final String SECTION_TYPE_RECOMMENDATION = "RECOMMENDATION";

    // ============================================================
    // Claim Types
    // ============================================================

    public static final String CLAIM_TYPE_CAREER_MOTIVATION = "CAREER_MOTIVATION";
    public static final String CLAIM_TYPE_WORK_STYLE = "WORK_STYLE";
    public static final String CLAIM_TYPE_ENVIRONMENT_FIT = "ENVIRONMENT_FIT";
    public static final String CLAIM_TYPE_PROFESSIONAL_STRENGTH = "PROFESSIONAL_STRENGTH";
    public static final String CLAIM_TYPE_PROFESSIONAL_CHALLENGE = "PROFESSIONAL_CHALLENGE";
    public static final String CLAIM_TYPE_ROLE_DIRECTION = "ROLE_DIRECTION";
    public static final String CLAIM_TYPE_DEVELOPMENT_PATH = "DEVELOPMENT_PATH";
    public static final String CLAIM_TYPE_ENTREPRENEURSHIP_MANAGEMENT = "ENTREPRENEURSHIP_MANAGEMENT";
    public static final String CLAIM_TYPE_CAREER_INCOME_LINK = "CAREER_INCOME_LINK";
    public static final String CLAIM_TYPE_CAREER_TIMING = "CAREER_TIMING";
    public static final String CLAIM_TYPE_CAREER_AUTHORITY = "CAREER_AUTHORITY";
    public static final String CLAIM_TYPE_CAREER_VISIBILITY = "CAREER_VISIBILITY";
    public static final String CLAIM_TYPE_CAREER_SCALE = "CAREER_SCALE";
    public static final String CLAIM_TYPE_CAREER_ACHIEVEMENT = "CAREER_ACHIEVEMENT";
    public static final String CLAIM_TYPE_CAREER_IDENTITY_ALIGNMENT = "CAREER_IDENTITY_ALIGNMENT";
    public static final String CLAIM_TYPE_CAREER_IDENTITY_TENSION = "CAREER_IDENTITY_TENSION";

    // ============================================================
    // Claim Scopes
    // ============================================================

    public static final String SCOPE_MOTIVATION = "MOTIVATION";
    public static final String SCOPE_WORK_METHOD = "WORK_METHOD";
    public static final String SCOPE_ENVIRONMENT = "ENVIRONMENT";
    public static final String SCOPE_ROLE = "ROLE";
    public static final String SCOPE_AUTHORITY = "AUTHORITY";
    public static final String SCOPE_PEOPLE_MANAGEMENT = "PEOPLE_MANAGEMENT";
    public static final String SCOPE_RESOURCE = "RESOURCE";
    public static final String SCOPE_INCOME = "INCOME";
    public static final String SCOPE_DEVELOPMENT = "DEVELOPMENT";
    public static final String SCOPE_TIMING = "TIMING";

    // ============================================================
    // Claim Strength
    // ============================================================

    public static final String STRENGTH_STRONG = "STRONG";
    public static final String STRENGTH_MODERATE = "MODERATE";
    public static final String STRENGTH_CONDITIONAL = "CONDITIONAL";
    public static final String STRENGTH_SUPPRESSED = "SUPPRESSED";

    // ============================================================
    // Evidence Families
    // ============================================================

    public static final String FAMILY_D1_STRUCTURAL = "D1_STRUCTURAL";
    public static final String FAMILY_D10_PROFESSIONAL = "D10_PROFESSIONAL";
    public static final String FAMILY_JAIMINI_ROLE = "JAIMINI_ROLE";
    public static final String FAMILY_PLANETARY_STRENGTH = "PLANETARY_STRENGTH";
    public static final String FAMILY_NAKSHATRA_EXPRESSION = "NAKSHATRA_EXPRESSION";
    public static final String FAMILY_YOGA_CONFIGURATION = "YOGA_CONFIGURATION";
    public static final String FAMILY_DASHA_ACTIVATION = "DASHA_ACTIVATION";

    // ============================================================
    // Evidence Priority
    // ============================================================

    public static final String PRIORITY_PRIMARY = "PRIMARY";
    public static final String PRIORITY_SECONDARY = "SECONDARY";
    public static final String PRIORITY_CONTEXTUAL = "CONTEXTUAL";
    public static final String PRIORITY_MODIFIER = "MODIFIER";

    // ============================================================
    // Support Direction
    // ============================================================

    public static final String DIRECTION_SUPPORT = "SUPPORT";
    public static final String DIRECTION_COUNTER = "COUNTER";
    public static final String DIRECTION_REFINE = "REFINE";
    public static final String DIRECTION_QUALIFY = "QUALIFY";

    // ============================================================
    // Cross System States
    // ============================================================

    public static final String CROSS_ALIGNED = "ALIGNED";
    public static final String CROSS_ALIGNED_STRONG = "ALIGNED_STRONG";
    public static final String CROSS_POTENTIAL_WITH_REALIZATION_FRICTION = "POTENTIAL_WITH_REALIZATION_FRICTION";
    public static final String CROSS_PROFESSIONALLY_DEVELOPED = "PROFESSIONALLY_DEVELOPED";
    public static final String CROSS_LAYERED_DIFFERENCE = "LAYERED_DIFFERENCE";
    public static final String CROSS_UNRESOLVED_CONFLICT = "UNRESOLVED_CONFLICT";

    // ============================================================
    // Shadbala Bands
    // ============================================================

    public static final String SHADBALA_STRONG = "STRONG";
    public static final String SHADBALA_ADEQUATE = "ADEQUATE";
    public static final String SHADBALA_BORDERLINE = "BORDERLINE";
    public static final String SHADBALA_LOW = "LOW";

    // Shadbala阈值
    public static final double SHADBALA_THRESHOLD_STRONG = 1.20;
    public static final double SHADBALA_THRESHOLD_ADEQUATE = 1.00;
    public static final double SHADBALA_THRESHOLD_BORDERLINE = 0.85;

    // ============================================================
    // Timing Stage Types
    // ============================================================

    public static final String STAGE_FOUNDATION = "FOUNDATION";
    public static final String STAGE_ACCUMULATION = "ACCUMULATION";
    public static final String STAGE_PRESSURE_TEST = "PRESSURE_TEST";
    public static final String STAGE_TRANSITION = "TRANSITION";
    public static final String STAGE_RESTRUCTURING = "RESTRUCTURING";
    public static final String STAGE_EXPANSION = "EXPANSION";
    public static final String STAGE_CONSOLIDATION = "CONSOLIDATION";
    public static final String STAGE_VISIBILITY = "VISIBILITY";
    public static final String STAGE_LEADERSHIP = "LEADERSHIP";

    // ============================================================
    // Activation Strength
    // ============================================================

    public static final String ACTIVATION_DIRECT = "DIRECT";
    public static final String ACTIVATION_STRONG = "STRONG";
    public static final String ACTIVATION_MODERATE = "MODERATE";
    public static final String ACTIVATION_WEAK = "WEAK";

    // ============================================================
    // Recommendation Types
    // ============================================================

    public static final String REC_TYPE_BUILD = "BUILD";
    public static final String REC_TYPE_ADJUST = "ADJUST";
    public static final String REC_TYPE_PREPARE = "PREPARE";

    // ============================================================
    // Recommendation Modes
    // ============================================================

    public static final String REC_MODE_DIRECT = "DIRECT";
    public static final String REC_MODE_CONDITIONAL = "CONDITIONAL";
    public static final String REC_MODE_VALIDATION_ONLY = "VALIDATION_ONLY";
    public static final String REC_MODE_PROHIBITED = "PROHIBITED";

    // ============================================================
    // Action Risk Levels
    // ============================================================

    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";

    // ============================================================
    // High Impact Action Blacklist
    // ============================================================

    public static final List<String> HIGH_IMPACTION_ACTION_BLACKLIST = Arrays.asList(
            "RESIGN_NOW",
            "QUIT_WITHOUT_PLAN",
            "START_BUSINESS_FULL_TIME",
            "CHANGE_CAREER_COMPLETELY",
            "ACCEPT_SPECIFIC_OFFER",
            "REJECT_SPECIFIC_OFFER",
            "MOVE_CITY_FOR_CAREER",
            "MOVE_COUNTRY_FOR_CAREER",
            "TAKE_LARGE_BUSINESS_DEBT",
            "INVEST_LARGE_CAPITAL",
            "END_BUSINESS_PARTNERSHIP",
            "START_SPECIFIC_PARTNERSHIP",
            "CONFRONT_MANAGER",
            "REFUSE_PROMOTION"
    );

    // ============================================================
    // Role Cluster IDs
    // ============================================================

    public static final String RC_RESEARCH_ANALYTICAL = "RC01_RESEARCH_ANALYTICAL";
    public static final String RC_ADVISORY_PROFESSIONAL = "RC02_ADVISORY_PROFESSIONAL";
    public static final String RC_EDUCATION_KNOWLEDGE = "RC03_EDUCATION_KNOWLEDGE";
    public static final String RC_COMMUNICATION_CONTENT = "RC04_COMMUNICATION_CONTENT";
    public static final String RC_SYSTEM_OPERATIONS = "RC05_SYSTEM_OPERATIONS";
    public static final String RC_PROJECT_EXECUTION = "RC06_PROJECT_EXECUTION";
    public static final String RC_TECHNICAL_PROBLEM_SOLVING = "RC07_TECHNICAL_PROBLEM_SOLVING";
    public static final String RC_CLIENT_COMMERCIAL = "RC08_CLIENT_COMMERCIAL";
    public static final String RC_RESOURCE_INTEGRATION = "RC09_RESOURCE_INTEGRATION";
    public static final String RC_CREATIVE_VALUE = "RC10_CREATIVE_VALUE";
    public static final String RC_MANAGEMENT_ORGANIZATION = "RC11_MANAGEMENT_ORGANIZATION";
    public static final String RC_HUMAN_SUPPORT_SERVICE = "RC12_HUMAN_SUPPORT_SERVICE";

    // ============================================================
    // Management Results
    // ============================================================

    public static final String MGMT_EXPERT = "EXPERT";
    public static final String MGMT_PROJECT_MANAGER = "PROJECT_MANAGER";
    public static final String MGMT_STRUCTURE_MANAGER = "STRUCTURE_MANAGER";
    public static final String MGMT_PEOPLE_MANAGER = "PEOPLE_MANAGER";
    public static final String MGMT_HYBRID = "HYBRID";
    public static final String MGMT_NOT_ENOUGH_EVIDENCE = "NOT_ENOUGH_EVIDENCE";

    // ============================================================
    // Entrepreneurship Results
    // ============================================================

    public static final String ENTREPRENEURSHIP_ENTREPRENEURIAL = "ENTREPRENEURIAL";
    public static final String ENTREPRENEURSHIP_INDEPENDENT_PROFESSIONAL = "INDEPENDENT_PROFESSIONAL";
    public static final String ENTREPRENEURSHIP_SMALL_TEAM_OPERATOR = "SMALL_TEAM_OPERATOR";
    public static final String ENTREPRENEURSHIP_ORGANIZATIONAL_AUTONOMY = "ORGANIZATIONAL_AUTONOMY";
    public static final String ENTREPRENEURSHIP_CONDITIONAL = "CONDITIONAL";
    public static final String ENTREPRENEURSHIP_NOT_ENOUGH_EVIDENCE = "NOT_ENOUGH_EVIDENCE";

    // ============================================================
    // D1 10H Sign Expression Tags
    // ============================================================

    public static final Map<String, List<String>> D1_10H_SIGN_TAGS = new HashMap<>();
    static {
        D1_10H_SIGN_TAGS.put("Aries", Arrays.asList("ACTION", "INITIATIVE", "COMPETITION", "SPEED"));
        D1_10H_SIGN_TAGS.put("Taurus", Arrays.asList("STABILITY", "VALUE", "RESOURCE", "CONSISTENCY"));
        D1_10H_SIGN_TAGS.put("Gemini", Arrays.asList("INFORMATION", "COMMUNICATION", "MULTI_ROLE", "ADAPTABILITY"));
        D1_10H_SIGN_TAGS.put("Cancer", Arrays.asList("PEOPLE", "SUPPORT", "CARE", "RESPONSIVENESS"));
        D1_10H_SIGN_TAGS.put("Leo", Arrays.asList("VISIBILITY", "LEADERSHIP", "CREATIVE_AUTHORITY"));
        D1_10H_SIGN_TAGS.put("Virgo", Arrays.asList("ANALYSIS", "PRECISION", "SERVICE", "PROCESS"));
        D1_10H_SIGN_TAGS.put("Libra", Arrays.asList("NEGOTIATION", "CLIENT", "BALANCE", "COLLABORATION"));
        D1_10H_SIGN_TAGS.put("Scorpio", Arrays.asList("DEPTH", "RESEARCH", "CRISIS", "TRANSFORMATION"));
        D1_10H_SIGN_TAGS.put("Sagittarius", Arrays.asList("KNOWLEDGE", "ADVISORY", "TEACHING", "EXPANSION"));
        D1_10H_SIGN_TAGS.put("Capricorn", Arrays.asList("STRUCTURE", "RESPONSIBILITY", "SYSTEM", "LONG_TERM"));
        D1_10H_SIGN_TAGS.put("Aquarius", Arrays.asList("SYSTEM", "NETWORK", "INNOVATION", "COLLECTIVE"));
        D1_10H_SIGN_TAGS.put("Pisces", Arrays.asList("IMAGINATION", "INTUITION", "SERVICE", "BOUNDARY_FLUIDITY"));
    }

    // ============================================================
    // D1 10L House Mapping
    // ============================================================

    public static final Map<String, String> D1_10L_HOUSE_MAP = new HashMap<>();
    static {
        D1_10L_HOUSE_MAP.put("1H", "IDENTITY / AUTONOMY / SELF_DIRECTION");
        D1_10L_HOUSE_MAP.put("2H", "SKILL_VALUE / SPEECH / RESOURCE / MONETIZATION");
        D1_10L_HOUSE_MAP.put("3H", "SELF_EFFORT / COMMUNICATION / INITIATIVE");
        D1_10L_HOUSE_MAP.put("4H", "FOUNDATION / PUBLIC_SUPPORT / PLACE_BASED");
        D1_10L_HOUSE_MAP.put("5H", "CREATIVE_INTELLIGENCE / TEACHING / LEADERSHIP_EXPRESSION");
        D1_10L_HOUSE_MAP.put("6H", "SERVICE / COMPETITION / PROBLEM_SOLVING / ROUTINE");
        D1_10L_HOUSE_MAP.put("7H", "CLIENT / PUBLIC / PARTNERSHIP / BUSINESS_INTERACTION");
        D1_10L_HOUSE_MAP.put("8H", "RESEARCH / CRISIS / COMPLEXITY / TRANSFORMATION");
        D1_10L_HOUSE_MAP.put("9H", "KNOWLEDGE / ADVISORY / LAW / TEACHING / GLOBAL");
        D1_10L_HOUSE_MAP.put("10H", "CAREER_CENTRALITY / PUBLIC_ROLE / RESPONSIBILITY");
        D1_10L_HOUSE_MAP.put("11H", "NETWORK / OUTCOME / SCALE / ORGANIZATION");
        D1_10L_HOUSE_MAP.put("12H", "FOREIGN / INSTITUTION / BACKSTAGE / REMOTE");
    }

    // ============================================================
    // Planets in D1 10H
    // ============================================================

    public static final Map<String, List<String>> D1_10H_PLANETS = new HashMap<>();
    static {
        D1_10H_PLANETS.put("Sun", Arrays.asList("AUTHORITY", "DECISION", "VISIBILITY", "LEADERSHIP", "PUBLIC_ROLE"));
        D1_10H_PLANETS.put("Moon", Arrays.asList("PUBLIC_CONTACT", "ADAPTABILITY", "PEOPLE_RESPONSE", "CARE", "CHANGEABILITY"));
        D1_10H_PLANETS.put("Mars", Arrays.asList("EXECUTION", "COMPETITION", "ACTION", "TECHNICAL", "CRISIS_HANDLING"));
        D1_10H_PLANETS.put("Mercury", Arrays.asList("ANALYSIS", "COMMUNICATION", "INFORMATION", "COMMERCE", "ADAPTABILITY"));
        D1_10H_PLANETS.put("Jupiter", Arrays.asList("KNOWLEDGE", "ADVISORY", "TEACHING", "JUDGMENT", "EXPANSION", "ETHICS"));
        D1_10H_PLANETS.put("Venus", Arrays.asList("VALUE", "AESTHETIC", "RELATIONSHIP", "DIPLOMACY", "CLIENT", "CREATIVE"));
        D1_10H_PLANETS.put("Saturn", Arrays.asList("STRUCTURE", "RESPONSIBILITY", "SYSTEM", "LONG_TERM_BUILDING", "OPERATIONS"));
        D1_10H_PLANETS.put("Rahu", Arrays.asList("AMBITION", "UNCONVENTIONAL", "INNOVATION", "FOREIGN", "SCALE", "VISIBILITY_SEEKING"));
        D1_10H_PLANETS.put("Ketu", Arrays.asList("SPECIALIZATION", "DETACHMENT", "RESEARCH", "NONLINEAR", "PRECISION"));
    }

    // ============================================================
    // D10 10L House Mapping
    // ============================================================

    public static final Map<String, String> D10_10L_HOUSE_MAP = new HashMap<>();
    static {
        D10_10L_HOUSE_MAP.put("1H", "PROFESSIONAL_AUTONOMY / PERSONAL_DIRECTION");
        D10_10L_HOUSE_MAP.put("2H", "PROFESSIONAL_VALUE / KNOWLEDGE / SKILL / EARNING_SUPPORT");
        D10_10L_HOUSE_MAP.put("3H", "INITIATIVE / COMMUNICATION / PROJECT_PUSH");
        D10_10L_HOUSE_MAP.put("4H", "INTERNAL_BUILDING / STABLE_PLATFORM");
        D10_10L_HOUSE_MAP.put("5H", "STRATEGY / CREATIVE_JUDGMENT / GUIDANCE");
        D10_10L_HOUSE_MAP.put("6H", "SERVICE / OPERATIONS / COMPETITION / PROBLEM_SOLVING");
        D10_10L_HOUSE_MAP.put("7H", "CLIENT / MARKET / BUSINESS_INTERACTION / PARTNERSHIP");
        D10_10L_HOUSE_MAP.put("8H", "COMPLEX_PROBLEMS / RISK / RESEARCH / RESTRUCTURING");
        D10_10L_HOUSE_MAP.put("9H", "ADVANCED_KNOWLEDGE / GUIDANCE / INSTITUTION / LAW / GLOBAL");
        D10_10L_HOUSE_MAP.put("10H", "RESPONSIBILITY / POSITION");
        D10_10L_HOUSE_MAP.put("11H", "OUTCOME / NETWORK / SCALE / RETURNS");
        D10_10L_HOUSE_MAP.put("12H", "FOREIGN / LARGE_INSTITUTION / BACKSTAGE / REMOTE");
    }

    // ============================================================
    // D10 10H Planets
    // ============================================================

    public static final Map<String, List<String>> D10_10H_PLANETS = new HashMap<>();
    static {
        D10_10H_PLANETS.put("Sun", Arrays.asList("AUTHORITY", "DECISION", "VISIBILITY", "FORMAL_RESPONSIBILITY"));
        D10_10H_PLANETS.put("Moon", Arrays.asList("PUBLIC_RESPONSE", "PEOPLE_MANAGEMENT", "ADAPTABILITY", "CLIENT_RESPONSE"));
        D10_10H_PLANETS.put("Mars", Arrays.asList("EXECUTION", "DECISION_SPEED", "COMPETITION", "TECHNICAL_ACTION"));
        D10_10H_PLANETS.put("Mercury", Arrays.asList("ANALYSIS", "INFORMATION", "COMMUNICATION", "COMMERCE", "COORDINATION"));
        D10_10H_PLANETS.put("Jupiter", Arrays.asList("JUDGMENT", "ADVISORY", "KNOWLEDGE", "MENTORSHIP", "INSTITUTIONAL_TRUST"));
        D10_10H_PLANETS.put("Venus", Arrays.asList("CLIENT_RELATION", "NEGOTIATION", "VALUE_CREATION", "DESIGN", "DIPLOMACY"));
        D10_10H_PLANETS.put("Saturn", Arrays.asList("OPERATIONS", "STRUCTURE", "RESPONSIBILITY", "MANAGEMENT", "SYSTEM_BUILDING"));
        D10_10H_PLANETS.put("Rahu", Arrays.asList("AMBITION", "SCALE", "INNOVATION", "UNCONVENTIONAL_ROLE", "FOREIGN_OR_TECH"));
        D10_10H_PLANETS.put("Ketu", Arrays.asList("SPECIALIZATION", "TECHNICAL_DEPTH", "RESEARCH", "NONCONVENTIONAL_PATH"));
    }

    // ============================================================
    // Nakshatra Career Expression Seeds
    // ============================================================

    public static final Map<String, List<String>> NAKSHATRA_EXPRESSION_SEEDS = new HashMap<>();
    static {
        NAKSHATRA_EXPRESSION_SEEDS.put("Ashwini", Arrays.asList("INITIATION", "SPEED", "MOBILITY", "REPAIR"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Bharani", Arrays.asList("ENDURANCE", "RESPONSIBILITY", "CONTROL", "TRANSFORMATION"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Krittika", Arrays.asList("DISCERNMENT", "CUTTING", "REFINEMENT", "STANDARD"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Rohini", Arrays.asList("GROWTH", "RESOURCE", "BUILDING", "ATTRACTION"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Mrigashira", Arrays.asList("SEARCH", "EXPLORATION", "CURIOSITY", "CONNECTION"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Ardra", Arrays.asList("ANALYSIS", "DISRUPTION", "INTENSITY", "REBUILDING"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Punarvasu", Arrays.asList("RESTORATION", "TEACHING", "REPEATABILITY", "EXPANSION"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Pushya", Arrays.asList("SUPPORT", "STRUCTURE", "NURTURE", "INSTITUTION"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Ashlesha", Arrays.asList("STRATEGY", "OBSERVATION", "COMPLEXITY", "INFLUENCE"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Magha", Arrays.asList("AUTHORITY", "LEGACY", "REPRESENTATION", "STATUS_AWARENESS"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Purva_Phalguni", Arrays.asList("CREATION", "RELATIONSHIP", "ATTRACTION", "ENJOYMENT"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Uttara_Phalguni", Arrays.asList("COMMITMENT", "CONTRACT", "ORGANIZATION", "RELIABILITY"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Hasta", Arrays.asList("SKILL", "EXECUTION", "MANIPULATION", "PRECISION"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Chitra", Arrays.asList("DESIGN", "FORM", "CONSTRUCTION", "DISTINCTION"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Swati", Arrays.asList("INDEPENDENCE", "FLEXIBILITY", "TRADE", "NETWORK"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Vishakha", Arrays.asList("GOAL_FOCUS", "COMPETITION", "DRIVE", "ACHIEVEMENT"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Anuradha", Arrays.asList("NETWORK", "LOYALTY", "COLLABORATION", "ORGANIZATION"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Jyeshtha", Arrays.asList("RESPONSIBILITY", "SENIORITY", "STRATEGY", "PROTECTION"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Mula", Arrays.asList("ROOT_CAUSE", "RESEARCH", "DECONSTRUCTION", "TRUTH_SEEKING"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Purva_Ashadha", Arrays.asList("ADVOCACY", "PERSUASION", "EXPANSION", "CONVICTION"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Uttara_Ashadha", Arrays.asList("LONG_TERM_GOAL", "AUTHORITY", "CONSISTENCY", "CONSENSUS"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Shravana", Arrays.asList("LISTENING", "LEARNING", "KNOWLEDGE_TRANSFER", "SYSTEM"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Dhanishta", Arrays.asList("PERFORMANCE", "RESOURCE_COORDINATION", "RHYTHM", "GROUP"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Shatabhisha", Arrays.asList("SYSTEM_ANALYSIS", "INDEPENDENCE", "TECHNOLOGY", "DIAGNOSIS"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Purva_Bhadrapada", Arrays.asList("INTENSITY", "IDEALISM", "TRANSFORMATION", "COMMITMENT"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Uttara_Bhadrapada", Arrays.asList("DEPTH", "STABILITY", "ENDURANCE", "LONG_RANGE"));
        NAKSHATRA_EXPRESSION_SEEDS.put("Revati", Arrays.asList("GUIDANCE", "CONNECTION", "COMPLETION", "ADAPTABILITY"));
    }

    // ============================================================
    // Planet Key Names
    // ============================================================

    public static final String PLANET_SUN = "Sun";
    public static final String PLANET_MOON = "Moon";
    public static final String PLANET_MARS = "Mars";
    public static final String PLANET_MERCURY = "Mercury";
    public static final String PLANET_JUPITER = "Jupiter";
    public static final String PLANET_VENUS = "Venus";
    public static final String PLANET_SATURN = "Saturn";
    public static final String PLANET_RAHU = "Rahu";
    public static final String PLANET_KETU = "Ketu";

    // ============================================================
    // Career Relevance Levels
    // ============================================================

    public static final String RELEVANCE_VERY_HIGH = "VERY_HIGH";
    public static final String RELEVANCE_HIGH = "HIGH";
    public static final String RELEVANCE_MEDIUM = "MEDIUM";
    public static final String RELEVANCE_LOW = "LOW";

    // ============================================================
    // Default Report Config
    // ============================================================

    public static final int DEFAULT_CORE_FINDINGS_COUNT = 4;
    public static final int MIN_CORE_FINDINGS = 3;
    public static final int MAX_CORE_FINDINGS = 5;
    public static final int MAX_ROLE_CLUSTERS_PRIMARY = 3;
    public static final int MAX_ROLE_CLUSTERS_SECONDARY = 1;
    public static final int MIN_CAREER_EXAMPLES = 3;
    public static final int MAX_CAREER_EXAMPLES = 6;
    public static final int MAX_TIMING_WINDOWS = 5;
    public static final int MIN_TIMING_WINDOWS = 2;
    public static final int MAX_RECOMMENDATIONS = 5;
    public static final int MIN_RECOMMENDATIONS = 3;

    // Timing default range (years from now)
    public static final int DEFAULT_TIMING_YEARS_AHEAD = 5;
}

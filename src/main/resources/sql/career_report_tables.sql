-- ============================================================
-- 事业深度报告数据库表
-- ============================================================

-- 报告实例表
CREATE TABLE IF NOT EXISTS career_report_instance (
    report_instance_id VARCHAR(64) PRIMARY KEY,
    report_type VARCHAR(32) NOT NULL DEFAULT 'CAREER',
    report_status VARCHAR(16) NOT NULL DEFAULT 'GENERATING',
    report_content_version VARCHAR(16) DEFAULT 'V1',
    report_schema_version VARCHAR(16) DEFAULT 'CAREER_V1',
    generated_at DATETIME,
    user_id VARCHAR(64) NOT NULL,
    primary_chart_id VARCHAR(64),
    chart_snapshot_id VARCHAR(64),
    dasha_snapshot_id VARCHAR(64),
    user_context_snapshot_id VARCHAR(64),
    source_skill VARCHAR(64) DEFAULT 'CAREER_REPORT_SKILL',
    source_skill_version VARCHAR(16) DEFAULT 'V1',
    analysis_engine_version VARCHAR(16) DEFAULT 'V1',
    renderer_version VARCHAR(16) DEFAULT 'V1',
    title VARCHAR(255),
    subtitle VARCHAR(500),
    subject_display_name VARCHAR(100),
    cover_summary TEXT,
    pdf_export_status VARCHAR(16) DEFAULT 'NOT_EXPORTED',
    pdf_file_id VARCHAR(64),
    pdf_generated_at DATETIME,
    derived_from_report_instance_id VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_status (user_id, report_status),
    INDEX idx_generated_at (generated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业深度报告实例表';

-- 报告章节表
CREATE TABLE IF NOT EXISTS career_report_section (
    section_id VARCHAR(64) PRIMARY KEY,
    report_instance_id VARCHAR(64) NOT NULL,
    section_type VARCHAR(32) NOT NULL,
    section_key VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    order_num INT DEFAULT 0,
    summary TEXT,
    body LONGTEXT,
    is_visible TINYINT(1) DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_report_order (report_instance_id, order_num),
    FOREIGN KEY (report_instance_id) REFERENCES career_report_instance(report_instance_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告章节表';

-- 报告Claim表
CREATE TABLE IF NOT EXISTS career_report_claim (
    claim_id VARCHAR(64) PRIMARY KEY,
    report_instance_id VARCHAR(64) NOT NULL,
    section_id VARCHAR(64),
    claim_type VARCHAR(64),
    claim_scope VARCHAR(32),
    statement TEXT,
    claim_strength VARCHAR(16),
    importance VARCHAR(16),
    cross_system_state VARCHAR(64),
    manifestation_capacity VARCHAR(16),
    friction_level VARCHAR(16),
    is_core TINYINT(1) DEFAULT 0,
    is_user_visible TINYINT(1) DEFAULT 1,
    suppression_reason VARCHAR(64),
    resolver_notes TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_report (report_instance_id),
    INDEX idx_section (section_id),
    INDEX idx_core (report_instance_id, is_core),
    FOREIGN KEY (report_instance_id) REFERENCES career_report_instance(report_instance_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告Claim表';

-- 报告证据表
CREATE TABLE IF NOT EXISTS career_report_evidence (
    evidence_id VARCHAR(64) PRIMARY KEY,
    report_instance_id VARCHAR(64) NOT NULL,
    astrological_fact_id VARCHAR(64),
    dedup_group_id VARCHAR(64),
    evidence_family VARCHAR(32),
    evidence_priority VARCHAR(16),
    evidence_type VARCHAR(64),
    source_chart VARCHAR(16),
    source_system VARCHAR(64),
    source_object VARCHAR(255),
    raw_configuration TEXT,
    interpreted_meaning TEXT,
    support_direction VARCHAR(16),
    support_strength VARCHAR(16),
    rule_id VARCHAR(64),
    source_skill VARCHAR(64),
    is_user_visible TINYINT(1) DEFAULT 1,
    user_facing_summary TEXT,
    independent_for_claim TINYINT(1) DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_report (report_instance_id),
    INDEX idx_family (report_instance_id, evidence_family),
    FOREIGN KEY (report_instance_id) REFERENCES career_report_instance(report_instance_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告证据表';

-- 报告时间窗口表
CREATE TABLE IF NOT EXISTS career_report_timing (
    timing_id VARCHAR(64) PRIMARY KEY,
    report_instance_id VARCHAR(64) NOT NULL,
    section_id VARCHAR(64),
    start_date DATE,
    end_date DATE,
    timing_type VARCHAR(32) DEFAULT 'DASHA_PHASE',
    mahadasa_lord VARCHAR(16),
    antardasha_lord VARCHAR(16),
    stage_type VARCHAR(32),
    theme VARCHAR(255),
    direction VARCHAR(16),
    intensity VARCHAR(16),
    activation_strength VARCHAR(16),
    manifestation_capacity VARCHAR(16),
    friction_level VARCHAR(16),
    user_facing_title VARCHAR(255),
    user_facing_summary TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_report (report_instance_id),
    INDEX idx_dates (start_date, end_date),
    FOREIGN KEY (report_instance_id) REFERENCES career_report_instance(report_instance_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告时间窗口表';

-- 报告建议表
CREATE TABLE IF NOT EXISTS career_report_recommendation (
    recommendation_id VARCHAR(64) PRIMARY KEY,
    report_instance_id VARCHAR(64) NOT NULL,
    section_id VARCHAR(64),
    recommendation_type VARCHAR(16),
    recommendation_mode VARCHAR(16),
    action_risk_level VARCHAR(16),
    specificity_level VARCHAR(16),
    statement TEXT,
    rationale TEXT,
    priority VARCHAR(16),
    is_user_visible TINYINT(1) DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_report (report_instance_id),
    INDEX idx_priority (report_instance_id, priority),
    FOREIGN KEY (report_instance_id) REFERENCES career_report_instance(report_instance_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告建议表';

-- 报告Claim关联表（支持证据、相关Claim等）
CREATE TABLE IF NOT EXISTS career_report_claim_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_id VARCHAR(64) NOT NULL,
    relation_type VARCHAR(32) NOT NULL, -- SUPPORTING_EVIDENCE, COUNTER_EVIDENCE, RELATED_CLAIM, RELATED_TIMING, RELATED_YOGA
    related_id VARCHAR(64) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_claim (claim_id),
    INDEX idx_related (related_id),
    FOREIGN KEY (claim_id) REFERENCES career_report_claim(claim_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告Claim关联表';

-- 报告Condition表
CREATE TABLE IF NOT EXISTS career_report_condition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_id VARCHAR(64) NOT NULL,
    condition_type VARCHAR(32) NOT NULL, -- CONDITION_FLAG, REQUIRED_CONDITION, COUNTER_CONDITION
    condition_value VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_claim (claim_id),
    FOREIGN KEY (claim_id) REFERENCES career_report_claim(claim_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事业报告Condition表';

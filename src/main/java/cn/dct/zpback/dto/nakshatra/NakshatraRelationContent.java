package cn.dct.zpback.dto.nakshatra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 星宿关系页面解析内容（Contract V2，docs/03 + renderer-policy.md）
 *
 * V2 关键变化：
 * - 新增 role_mapping_text / family_explainer / family_boundary_note；
 * - 新增四个方向标题字段（a_to_b_title / b_to_a_title / mutual_resonance_title / mutual_blindspot_title），
 *   页面必须直接显示姓名与角色，不用箭头替代；
 * - 命之星拆成 mutual_resonance + mutual_blindspot 两模块；
 * - 删除 mutual_influence（V1 行为）；
 * - 正文只允许 {{name_a}} / {{name_b}} 占位符，且必须在返回前完成绑定。
 *
 * 注意：application.yml 全局设置了 spring.jackson.default-property-inclusion=non_null，
 * 但 resolve_response.schema.json 的 content 块把这些字段列为 required，
 * 条件互斥时必须是显式 null。因此这里用 @JsonInclude(ALWAYS) 覆盖全局策略。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class NakshatraRelationContent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("content_mode")
    private String contentMode;

    @JsonProperty("influence_mode")
    private String influenceMode;

    /** 姓名 + 角色映射，如「若岚（友） × 陈舟（衰）」；命之星为「清和 × 予安」 */
    @JsonProperty("role_mapping_text")
    private String roleMappingText;

    /** 关系族解释（按 family_code 取固定文案） */
    @JsonProperty("family_explainer")
    private String familyExplainer;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("relationship_tone")
    private String relationshipTone;

    /** DIRECTIONAL 时非空 */
    @JsonProperty("a_to_b_title")
    private String aToBTitle;

    /** DIRECTIONAL 时非空；MUTUAL 时为 null */
    @JsonProperty("a_to_b")
    private String aToB;

    /** DIRECTIONAL 时非空 */
    @JsonProperty("b_to_a_title")
    private String bToATitle;

    /** DIRECTIONAL 时非空；MUTUAL 时为 null */
    @JsonProperty("b_to_a")
    private String bToA;

    /** MUTUAL 时非空（「你们容易产生默契的地方」）；DIRECTIONAL 时为 null */
    @JsonProperty("mutual_resonance_title")
    private String mutualResonanceTitle;

    /** MUTUAL 时非空；DIRECTIONAL 时为 null */
    @JsonProperty("mutual_resonance")
    private String mutualResonance;

    /** MUTUAL 时非空（「你们容易一起卡住的地方」）；DIRECTIONAL 时为 null */
    @JsonProperty("mutual_blindspot_title")
    private String mutualBlindspotTitle;

    /** MUTUAL 时非空；DIRECTIONAL 时为 null */
    @JsonProperty("mutual_blindspot")
    private String mutualBlindspot;

    @JsonProperty("relationship_key")
    private String relationshipKey;

    /** 关系族边界提示，注册表为空时为 null，前端非空才展示 */
    @JsonProperty("family_boundary_note")
    private String familyBoundaryNote;

    @JsonProperty("disclaimer_short")
    private String disclaimerShort;
}

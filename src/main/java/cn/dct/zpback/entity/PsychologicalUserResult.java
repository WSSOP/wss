package cn.dct.zpback.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 心理测评中心｜用户结果实体
 *
 * 对应表：psychological_user_result
 *
 * 边界：
 * - level/level_label/level_color/advice_text 均为结果快照，
 *   运营修改量表的等级文案后，历史结果不受影响；
 * - finished_at 为空 = 未完成（中途退出）。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PsychologicalUserResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String resultId;
    private String userId;
    private String assessmentId;
    private Integer totalScore;
    private String level;
    private String levelLabel;
    private String levelColor;
    private String adviceText;
    private String finishReason;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

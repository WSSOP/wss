package cn.dct.zpback.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private String userId;

    /** 手机号 */
    private String phone;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatar;

    /** 性别：male/female/others */
    private String gender;

    /** 星印余额 */
    private Integer starBalance;

    /** 用户等级：free/vip/svip */
    private String level;

    /** VIP过期时间 */
    private LocalDateTime vipExpireAt;

    /** 每日免费次数剩余 */
    private Integer dailyFreeRemaining;

    /** 额外次数包剩余 */
    private Integer extraPackRemaining;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

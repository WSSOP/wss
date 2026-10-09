package cn.dct.zpback.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 星盘实体
 * 记录用户保存的星盘信息
 */
@Data
public class Chart implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 星盘ID */
    private String chartId;

    /** 用户ID */
    private String userId;

    /** 姓名 */
    private String name;

    /** 性别: male/female */
    private String gender;

    /** 出生日期 */
    private String birthDate;

    /** 出生时间 */
    private String birthTime;

    /** 出生地点 */
    private String birthPlace;

    /** 纬度 */
    private Double latitude;

    /** 经度 */
    private Double longitude;

    /** 时区 */
    private String timezone;

    /** 是否为默认星盘 */
    private Boolean isDefault;

    /** 星盘计算数据（JSON） */
    private String chartData;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

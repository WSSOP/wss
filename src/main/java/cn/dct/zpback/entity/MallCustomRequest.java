package cn.dct.zpback.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定制需求单。§2.9：提交只生成 request_id，不产生订单 / 待付款 / 锁库存 / 应收金额。
 * 定制字段由后台动态配置，统一存 JSON，不在表里写死固定列。
 */
@Data
public class MallCustomRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String requestId;
    private String userId;
    private String productId;

    /** 定制字段 payload（JSON） */
    private String payload;

    /** SUBMITTED / QUOTED / CONFIRMED；V1 停在 SUBMITTED */
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
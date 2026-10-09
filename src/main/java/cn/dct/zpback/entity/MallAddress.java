package cn.dct.zpback.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 商城收货地址；下单时存快照进订单，此后改地址不影响已下订单。 */
@Data
public class MallAddress implements Serializable {

    private static final long serialVersionUID = 1L;

    private String addressId;
    private String userId;

    private String name;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    /** 列 is_default，MyBatis 下划线映射到 isDefault */
    private Boolean isDefault;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
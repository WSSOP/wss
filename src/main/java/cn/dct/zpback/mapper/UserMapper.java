package cn.dct.zpback.mapper;

import cn.dct.zpback.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper {

    /** 根据ID查询用户 */
    User selectById(@Param("userId") String userId);

    /** 根据手机号查询用户 */
    User selectByPhone(@Param("phone") String phone);

    /** 插入用户 */
    int insert(User user);

    /** 更新用户 */
    int update(User user);

    /** 更新星印余额 */
    int updateStarBalance(@Param("userId") String userId, @Param("delta") int delta);

    /** 更新每日免费次数 */
    int updateDailyFree(@Param("userId") String userId, @Param("remaining") int remaining);
}

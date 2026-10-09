package cn.dct.zpback.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存服务
 * 封装常用 Redis 操作：String、Object、分布式锁、计数器
 */
@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    // ============================================================
    // String 操作（Token、计数器等）
    // ============================================================

    /** 设置 String 值 */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /** 设置 String 值并指定过期时间 */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 获取 String 值 */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /** 删除 key */
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    /** 判断 key 是否存在 */
    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    /** 设置过期时间 */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return stringRedisTemplate.expire(key, timeout, unit);
    }

    /** 原子递增 */
    public Long increment(String key) {
        return stringRedisTemplate.opsForValue().increment(key);
    }

    /** 原子递增指定步长 */
    public Long increment(String key, long delta) {
        return stringRedisTemplate.opsForValue().increment(key, delta);
    }

    // ============================================================
    // Object 操作（缓存对象）
    // ============================================================

    /** 设置 Object 值 */
    public void setObject(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /** 设置 Object 值并指定过期时间 */
    public void setObject(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 获取 Object 值 */
    public Object getObject(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // ============================================================
    // 问星渡业务缓存 Key 常量
    // ============================================================

    /** 用户 Token 缓存 Key 前缀 */
    public static final String KEY_USER_TOKEN = "user:token:";

    /** 用户信息缓存 Key 前缀 */
    public static final String KEY_USER_INFO = "user:info:";

    /** 报告实例缓存 Key 前缀 */
    public static final String KEY_REPORT_INSTANCE = "report:instance:";

    /** 追问请求状态缓存 Key 前缀 */
    public static final String KEY_FOLLOWUP_REQUEST = "followup:request:";

    /** 会话上下文缓存 Key 前缀 */
    public static final String KEY_CONVERSATION_CONTEXT = "conversation:context:";

    /** 权益预占缓存 Key 前缀 */
    public static final String KEY_ENTITLEMENT_RESERVATION = "entitlement:reservation:";

    /** 幂等键缓存 Key 前缀 */
    public static final String KEY_IDEMPOTENCY = "idempotency:";

    /** 每日免费次数缓存 Key 前缀 */
    public static final String KEY_DAILY_FREE = "daily:free:";

    /** 报告赠送追问缓存 Key 前缀 */
    public static final String KEY_REPORT_GIFT = "report:gift:";

    /** 星盘数据缓存 Key 前缀 */
    public static final String KEY_CHART_DATA = "chart:data:";

    /** 报告全文缓存 Key 前缀 */
    public static final String KEY_REPORT_FULLTEXT = "report:fulltext:";

    /** 会话活跃时间缓存 Key 前缀 */
    public static final String KEY_CONVERSATION_ACTIVE = "conversation:active:";
}

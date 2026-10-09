package cn.dct.zpback.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * userId 解析口径测试
 *
 * 回归背景：行星珠宝的选盘接口曾用 {@code "user_" + authHeader.hashCode()}
 * 解析身份，而 ChartController 用的是 {@code token_<手机号>} 直取，
 * 导致同一份星盘在两个接口下对应不同 userId，表现为"以前建好的星盘选不到了"。
 */
class BearerUserIdResolverTest {

    private final BearerUserIdResolver resolver = new BearerUserIdResolver();

    @Test
    @DisplayName("生产 token：手机号即 userId，与 ChartController 存量数据一致")
    void productionToken_usesPhoneNumber() {
        assertEquals("user_13232123212", resolver.resolve("Bearer token_13232123212"));
        assertEquals("user_610637873", resolver.resolve("Bearer token_610637873"));
    }

    @Test
    @DisplayName("mock token：沿用历史 hashCode 口径，不改变既有 mock 用户")
    void mockToken_keepsHistoricalHash() {
        String token = "mock_token_abc123";
        String expected = "user_" + Math.abs(token.hashCode());
        assertEquals(expected, resolver.resolve("Bearer " + token));
    }

    @Test
    @DisplayName("同一 token 必须解析出同一个 userId（跨接口稳定性）")
    void sameToken_alwaysSameUserId() {
        String a = resolver.resolve("Bearer token_13232123212");
        String b = resolver.resolve("Bearer token_13232123212");
        assertEquals(a, b);
    }

    @Test
    @DisplayName("不同 token 解析出不同 userId")
    void differentTokens_differentUsers() {
        assertNotEquals(resolver.resolve("Bearer token_13800138000"),
                resolver.resolve("Bearer token_13232123212"));
    }

    @Test
    @DisplayName("未登录 / 非法 header 一律 anonymous，不得抛异常")
    void missingOrInvalidHeader_isAnonymous() {
        assertEquals(BearerUserIdResolver.ANONYMOUS, resolver.resolve(null));
        assertEquals(BearerUserIdResolver.ANONYMOUS, resolver.resolve(""));
        assertEquals(BearerUserIdResolver.ANONYMOUS, resolver.resolve("Basic abc"));
        assertEquals(BearerUserIdResolver.ANONYMOUS, resolver.resolve("Bearer "));
        assertEquals(BearerUserIdResolver.ANONYMOUS, resolver.resolve("Bearer "));
    }

    @Test
    @DisplayName("token 带首尾空白时仍能解析")
    void tokenWithWhitespace_stillResolves() {
        assertEquals("user_13232123212", resolver.resolve("Bearer  token_13232123212"));
    }
}
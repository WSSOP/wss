package cn.dct.zpback.security;

import org.springframework.stereotype.Component;

/**
 * Authorization 头 → userId 的唯一解析口径。
 *
 * 背景：本项目历史上每个 Controller 各自抄了一份解析逻辑，导致同一份星盘
 * 在不同接口下对应不同的 userId（{@code token_<手机号>} / {@code mock_token_*}
 * / {@code hashCode()} / {@code u_001}），表现为"建盘列表有数据、别的功能却查不到"。
 *
 * 规范化规则（与 ChartController 历史行为完全一致，保证存量数据继续可见）：
 * <ul>
 *   <li>{@code token_<数字>}      → {@code user_<数字>}   （生产 token，手机号即 userId）</li>
 *   <li>{@code mock_token_<任意>} → {@code user_} + |hashCode| （本地 mock 登录）</li>
 *   <li>其它 / 无 header          → {@code anonymous}</li>
 * </ul>
 *
 * 新代码必须注入本类，不要再在自己的 Controller 里写解析分支。
 */
@Component
public class BearerUserIdResolver {

    /** 未登录时的兜底身份，与历史行为保持一致 */
    public static final String ANONYMOUS = "anonymous";

    private static final String BEARER = "Bearer ";
    private static final String TOKEN_PREFIX = "token_";
    private static final String MOCK_PREFIX = "mock_token_";

    public String resolve(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER)) {
            return ANONYMOUS;
        }
        String token = authHeader.substring(BEARER.length());
        if (token == null) {
            return ANONYMOUS;
        }
        token = token.trim();
        if (token.startsWith(TOKEN_PREFIX)) {
            return "user_" + token.substring(TOKEN_PREFIX.length());
        }
        if (token.startsWith(MOCK_PREFIX)) {
            return "user_" + Math.abs(token.hashCode());
        }
        return ANONYMOUS;
    }
}
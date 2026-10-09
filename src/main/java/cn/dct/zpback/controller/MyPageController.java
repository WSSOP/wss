package cn.dct.zpback.controller;

import cn.dct.zpback.dto.ApiResponse;
import cn.dct.zpback.dto.mypage.MyPageBootstrap;
import cn.dct.zpback.security.BearerUserIdResolver;
import cn.dct.zpback.service.MyPageAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问星渡｜【我的】一级页
 *
 * 对应文档《【我的】页面开发文档》STEP 7，
 * 实际路径 GET {server.servlet.context-path}/v1/my-page/bootstrap
 * （项目 context-path 已含 /api，与 LearningCourseController 保持一致）。
 *
 * <b>与文档的对应关系</b>：
 * <ul>
 *   <li>第 1 节「只读取摘要和状态」→ 返回 {@link MyPageBootstrap}，
 *       里面没有报告正文、订单明细、消息内容等业务实例数据；</li>
 *   <li>第 8 节「不隐藏功能入口」→ 未登录时同样返回全部入口，
 *       只是 identity/wallet 为 null；</li>
 *   <li>第 19.5 节「单个摘要失败不阻塞整个页面」→
 *       聚合服务内部各子查询独立降级，这里不额外兜底整页。</li>
 * </ul>
 *
 * <b>登录回跳白名单</b>（文档第 15 节）不在后端做，
 * 因为前端跳转前就能判断；后端只负责返回权威状态。
 */
@RestController
@RequestMapping("/v1/my-page")
@RequiredArgsConstructor
@Slf4j
public class MyPageController {

    private final MyPageAggregationService aggregation;
    private final BearerUserIdResolver bearer;

    /**
     * GET /api/v1/my-page/bootstrap
     *
     * 未登录不返回 401：文档要求一级页始终可见，
     * 未登录时返回 loggedIn=false + 完整入口结构即可。
     * 返回 401 会让用户点开 App 就撞到登录墙。
     */
    @GetMapping("/bootstrap")
    public ApiResponse<MyPageBootstrap> bootstrap(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String userId = bearer.resolve(auth);
        return ApiResponse.success(aggregation.bootstrap(userId));
    }
}

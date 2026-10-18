package com.shiro.shiroaiagent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shiro.shiroaiagent.service.QaCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 缓存管理接口（用于缓存效果验证与实验）
 *
 * <p>这几个接口的价值在于：<b>让"缓存收益"可被量化验证</b>——
 * 重置统计 → 跑一轮问题 → 读取命中率，即可得到论文里要的数字。</p>
 */
@Tag(name = "管理端 · 缓存", description = "缓存统计与清理（超管）")
@RestController
@RequestMapping("/admin/cache")
public class AdminCacheController {

    @Resource
    private QaCacheService qaCacheService;

    @Operation(summary = "缓存统计",
            description = "返回命中次数、未命中次数与命中率。做'有缓存 vs 无缓存'对比实验时，先重置再跑一轮。")
    @GetMapping("/stat")
    public Map<String, Object> stat() {
        StpUtil.checkRole("super_admin");
        QaCacheService.CacheStat stat = qaCacheService.stat();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hit", stat.hit());
        result.put("miss", stat.miss());
        result.put("total", stat.total());
        result.put("hitRate", stat.hitRate());
        result.put("hitRatePercent", String.format("%.1f%%", stat.hitRate() * 100));
        return result;
    }

    @Operation(summary = "重置缓存统计", description = "做对比实验前后各调用一次，避免历史数据干扰")
    @PostMapping("/stat/reset")
    public Map<String, Object> resetStat() {
        StpUtil.checkRole("super_admin");
        qaCacheService.resetStat();
        return Map.of("success", true, "message", "缓存统计已重置");
    }

    @Operation(summary = "清除指定用户的问答缓存",
            description = "管理端修改某学生成绩后可调用，避免'成绩已更新但问答仍返回旧值'")
    @DeleteMapping("/evict")
    public Map<String, Object> evict(@RequestParam Long userId) {
        StpUtil.checkRole("super_admin");
        long removed = qaCacheService.evictUser(userId);
        return Map.of("success", true, "removed", removed,
                "message", "已清除用户 " + userId + " 的问答缓存 " + removed + " 条");
    }
}

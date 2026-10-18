package com.shiro.shiroaiagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiro.shiroaiagent.vo.CampusChatResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 问答结果缓存（Redis）
 *
 * <p><b>为什么值得缓存：</b>校园问答的重复率极高——"图书馆几点开门""怎么补办学生证"
 * 这类问题会被不同学生反复提问。每次问答都要经过
 * <b>检索 + Embedding + 大模型生成</b>（慢且花钱），而相同问题的答案是稳定的，
 * 缓存能同时降低 <b>响应时延</b> 与 <b>模型调用成本</b>。</p>
 *
 * <p><b>★ 安全设计：缓存 key 必须包含用户维度 ★</b></p>
 * <p>如果只按"问题文本"缓存，会出现严重的越权问题：
 * 学生 A 问"我的绩点是多少"缓存了 3.34，学生 B 问同一句话就会拿到 <b>A 的绩点</b>——
 * <b>缓存绕过了权限控制</b>。因此 key 的构成是：
 * {@code qa:cache:{userId}:{问题hash}}，不同用户天然隔离。</p>
 *
 * <p><b>更进一步：</b>对于<b>个人数据类</b>问题，即使 key 带了 userId 也仍然谨慎——
 * 因为成绩可能被管理员修改，缓存会导致"改了成绩但问答还是旧值"。
 * 因此本实现允许上层通过 {@code cacheable} 参数控制某类问题是否参与缓存，
 * 且个人数据类问题的 TTL 更短。</p>
 */
@Service
@Slf4j
public class QaCacheService {

    /** 缓存 key 前缀 */
    private static final String KEY_PREFIX = "qa:cache:";

    /** 命中率统计的 key */
    private static final String STAT_HIT = "qa:stat:hit";
    private static final String STAT_MISS = "qa:stat:miss";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 缓存总开关（便于做"有缓存 vs 无缓存"的对比实验） */
    @Value("${campus.qa.cache.enabled:true}")
    private boolean cacheEnabled;

    /** 知识类问题缓存时长（分钟） */
    @Value("${campus.qa.cache.knowledge-ttl-minutes:1440}")
    private long knowledgeTtlMinutes;

    /** 个人数据类问题缓存时长（分钟）——设短，避免成绩更新后长期返回旧值 */
    @Value("${campus.qa.cache.personal-ttl-minutes:5}")
    private long personalTtlMinutes;

    public QaCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 读取缓存
     *
     * @param userId            当前用户 id（<b>必须参与 key 计算</b>）
     * @param question          用户问题
     * @return 命中返回响应对象；未命中返回 null
     */
    public CampusChatResponseVO get(Long userId, String question) {
        if (!cacheEnabled || userId == null || StrUtil.isBlank(question)) {
            return null;
        }
        String key = buildKey(userId, question);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (StrUtil.isBlank(json)) {
                redisTemplate.opsForValue().increment(STAT_MISS);
                return null;
            }
            try {
                CampusChatResponseVO vo = objectMapper.readValue(json, CampusChatResponseVO.class);
                // 命中：标记出来，便于前端展示与统计核对
                vo.setCacheHit(true);
                redisTemplate.opsForValue().increment(STAT_HIT);
                log.info("问答缓存命中：key={}", key);
                return vo;
            } catch (Exception parseEx) {
                // ⚠️ 解析失败必须也计入 miss，否则统计会出现
                //    "miss 数比请求数少"的诡异现象（真实踩过：缓存写入成功却永不命中）。
                redisTemplate.opsForValue().increment(STAT_MISS);
                // 清掉坏数据，避免后续请求反复在同一个 key 上失败
                redisTemplate.delete(key);
                log.warn("缓存内容解析失败，已删除该缓存并降级为正常问答：key={}, 原因={}",
                        key, parseEx.getMessage());
                return null;
            }
        } catch (Exception e) {
            // 缓存故障不应影响主流程：降级为直接走正常问答
            log.warn("读取问答缓存失败，降级为正常问答：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 写入缓存
     *
     * @param userId     当前用户 id
     * @param question   用户问题
     * @param response   问答结果
     * @param personalData 是否为个人数据类问题（决定 TTL 长短）
     */
    public void put(Long userId, String question, CampusChatResponseVO response, boolean personalData) {
        if (!cacheEnabled || userId == null || StrUtil.isBlank(question) || response == null) {
            return;
        }
        // 权限拦截类响应不缓存（与身份状态相关，缓存容易出错）
        if (response.getChannel() != null && response.getChannel().startsWith("拒绝")) {
            return;
        }
        String key = buildKey(userId, question);
        long ttl = personalData ? personalTtlMinutes : knowledgeTtlMinutes;
        try {
            // 缓存时不带 cacheHit 标记，避免下次命中时信息错乱
            response.setCacheHit(false);
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(ttl));
            log.info("问答结果已缓存：key={}, ttl={}分钟, personalData={}", key, ttl, personalData);
        } catch (Exception e) {
            log.warn("写入问答缓存失败（不影响回答）：{}", e.getMessage());
        }
    }

    /**
     * 清除某个用户的全部问答缓存
     *
     * <p>使用场景：管理端修改了该学生的成绩后调用，
     * 避免"成绩已更新但问答仍返回旧值"。</p>
     *
     * @return 清除的 key 数量
     */
    public long evictUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        try {
            var keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            Long removed = redisTemplate.delete(keys);
            log.info("已清除用户 {} 的问答缓存 {} 条", userId, removed);
            return removed == null ? 0 : removed;
        } catch (Exception e) {
            log.warn("清除用户缓存失败：{}", e.getMessage());
            return 0;
        }
    }

    /**
     * 清除<b>全部</b>知识类问答缓存（知识库内容变更后调用）
     *
     * <p><b>为什么必须清：</b>知识类答案缓存 24 小时。管理员刚入库一篇新文档、
     * 或删掉一篇过期文档后，如果不清缓存，学生问同一个问题会拿到"知识库里
     * 已经没有的旧答案"——表现为"我明明加了文档，问答却还是说没有相关说明"，
     * 这类问题极难排查（看起来像检索失效，其实是缓存没失效）。</p>
     *
     * <p>缓存 key 是 {@code qa:cache:*}，与统计用的 {@code qa:stat:*} 互不影响。</p>
     *
     * @return 清除的 key 数量
     */
    public long evictAllKnowledge() {
        try {
            var keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            Long removed = redisTemplate.delete(keys);
            long count = removed == null ? 0 : removed;
            log.info("知识库变更，已清除全部问答缓存 {} 条", count);
            return count;
        } catch (Exception e) {
            // 缓存清理失败不应让入库/删除失败
            log.warn("清除问答缓存失败（不影响知识库变更）：{}", e.getMessage());
            return 0;
        }
    }

    /**
     * 缓存统计：命中/未命中次数与命中率
     */
    public CacheStat stat() {
        long hit = parseLong(redisTemplate.opsForValue().get(STAT_HIT));
        long miss = parseLong(redisTemplate.opsForValue().get(STAT_MISS));
        long total = hit + miss;
        double rate = total == 0 ? 0.0 : (double) hit / total;
        return new CacheStat(hit, miss, total, rate);
    }

    /** 重置统计（做对比实验前后各调用一次） */
    public void resetStat() {
        redisTemplate.delete(STAT_HIT);
        redisTemplate.delete(STAT_MISS);
        log.info("缓存统计已重置");
    }

    /**
     * 构造缓存 key：{@code qa:cache:{userId}:{问题MD5}}
     *
     * <p><b>userId 必须在 key 里</b>——这是防止缓存绕过权限的关键。</p>
     */
    private String buildKey(Long userId, String question) {
        String normalized = question.trim().toLowerCase();
        String hash = DigestUtil.md5Hex(normalized);
        return KEY_PREFIX + userId + ":" + hash;
    }

    private long parseLong(String value) {
        try {
            return StrUtil.isBlank(value) ? 0L : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 缓存统计结果
     *
     * @param hit    命中次数
     * @param miss   未命中次数
     * @param total  总次数
     * @param hitRate 命中率（0~1）
     */
    public record CacheStat(long hit, long miss, long total, double hitRate) {
    }
}

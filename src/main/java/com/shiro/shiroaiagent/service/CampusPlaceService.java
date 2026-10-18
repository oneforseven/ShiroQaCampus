package com.shiro.shiroaiagent.service;

import com.shiro.shiroaiagent.entity.CampusPlace;
import com.shiro.shiroaiagent.mapper.CampusPlaceMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 校园导航服务：办事地点、办公时间与联系方式
 *
 * 这类问题（"补办学生证去哪办""图书馆几点开门"）有确定答案，
 * 由结构化查询直接给出，不交给 RAG 生成，避免模型编造地点或时间。
 */
@Service
@Slf4j
public class CampusPlaceService {

    @Resource
    private CampusPlaceMapper campusPlaceMapper;

    /**
     * 按关键词查询办事地点；关键词为空则返回全部
     */
    public List<CampusPlace> search(String keyword) {
        List<CampusPlace> places = campusPlaceMapper.search(keyword);
        log.info("查询校园办事地点：keyword={}, 命中{}条", keyword, places.size());
        return places;
    }
}

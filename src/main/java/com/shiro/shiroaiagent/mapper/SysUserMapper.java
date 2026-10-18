package com.shiro.shiroaiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiro.shiroaiagent.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账号数据访问（下一步做登录时使用）
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}

package com.shiro.shiroaiagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录请求
 */
@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "登录名（学生填学号，如 2023001；管理员填 admin）", example = "2023001")
    private String username;

    @Schema(description = "密码（模拟数据统一为 123456）", example = "123456")
    private String password;
}

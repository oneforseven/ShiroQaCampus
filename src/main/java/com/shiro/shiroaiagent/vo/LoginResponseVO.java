package com.shiro.shiroaiagent.vo;

import com.shiro.shiroaiagent.security.LoginUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应：token + 当前用户信息
 *
 * <p>前端拿到后需在后续请求中携带：请求头 {@code tokenName: tokenValue}
 * （本项目 tokenName 配置为 {@code satoken}）。</p>
 *
 * <p>保留 {@link NoArgsConstructor}：与 {@code CitationVO} 同理，
 * 供 Jackson 反序列化使用（若该对象将来参与缓存或消息传递）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginResponseVO {

    @Schema(description = "token 名称（请求头名）", example = "satoken")
    private String tokenName;

    @Schema(description = "token 值")
    private String tokenValue;

    @Schema(description = "登录用户信息")
    private LoginUser user;
}

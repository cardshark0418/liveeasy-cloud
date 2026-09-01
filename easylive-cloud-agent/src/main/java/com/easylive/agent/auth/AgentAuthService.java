package com.easylive.agent.auth;

import com.easylive.auth.UserAuthComponent;
import com.easylive.entity.vo.UserLoginDto;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

/** 从双令牌 Cookie 解析当前用户（Access 过期时静默续期），不信任前端传入的用户 ID。 */
@Service
public class AgentAuthService {

    private final UserAuthComponent userAuthComponent;

    public AgentAuthService(UserAuthComponent userAuthComponent) {
        this.userAuthComponent = userAuthComponent;
    }

    public UserLoginDto getCurrentUser(HttpServletRequest request) {
        return userAuthComponent.resolveUser(request);
    }
}

package com.easylive.auth;

import cn.hutool.core.util.StrUtil;
import com.easylive.entity.constants.Constants;
import com.easylive.entity.vo.UserLoginDto;
import com.easylive.redis.RedisUtils;
import com.easylive.utils.CookieUtil;
import com.easylive.utils.JwtUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * 用户双令牌：JWT Access（Cookie）+ UUID Refresh（Redis）。
 * Access 过期时在鉴权路径静默续发新 Access（不轮换 Refresh）。
 */
@Component
public class UserAuthComponent {

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private JwtUtils jwtUtils;

    public void issueTokens(UserLoginDto userLoginDto, HttpServletResponse response) {
        // 单端：踢掉旧 RT
        Object oldRt = redisUtils.get(Constants.REDIS_KEY_USER_REFRESH_TOKEN + userLoginDto.getUserId());
        if (oldRt != null) {
            redisUtils.delete(Constants.REDIS_KEY_REFRESH_TOKEN + oldRt);
            redisUtils.delete(Constants.REDIS_KEY_USER_REFRESH_TOKEN + userLoginDto.getUserId());
        }

        String refreshToken = UUID.randomUUID().toString();
        redisUtils.setex(Constants.REDIS_KEY_REFRESH_TOKEN + refreshToken, userLoginDto.getUserId(), Constants.REFRESH_TOKEN_EXPIRE_MS);
        redisUtils.setex(Constants.REDIS_KEY_USER_REFRESH_TOKEN + userLoginDto.getUserId(), refreshToken, Constants.REFRESH_TOKEN_EXPIRE_MS);

        String accessToken = jwtUtils.generateAccessToken(userLoginDto);
        userLoginDto.setToken(null);
        userLoginDto.setExpireAt(System.currentTimeMillis() + Constants.JWT_ACCESS_EXPIRE_SECONDS * 1000);

        CookieUtil.setAccessTokenCookie(response, accessToken);
        CookieUtil.setRefreshTokenCookie(response, refreshToken);
        CookieUtil.clearCookieCompatToken(response);
    }

    public void reissueAccessToken(UserLoginDto userLoginDto, HttpServletResponse response) {
        String accessToken = jwtUtils.generateAccessToken(userLoginDto);
        userLoginDto.setExpireAt(System.currentTimeMillis() + Constants.JWT_ACCESS_EXPIRE_SECONDS * 1000);
        CookieUtil.setAccessTokenCookie(response, accessToken);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = CookieUtil.getRefreshToken(request);
        if (StrUtil.isNotEmpty(refreshToken)) {
            Object userId = redisUtils.get(Constants.REDIS_KEY_REFRESH_TOKEN + refreshToken);
            redisUtils.delete(Constants.REDIS_KEY_REFRESH_TOKEN + refreshToken);
            if (userId != null) {
                Object bound = redisUtils.get(Constants.REDIS_KEY_USER_REFRESH_TOKEN + userId);
                if (refreshToken.equals(String.valueOf(bound))) {
                    redisUtils.delete(Constants.REDIS_KEY_USER_REFRESH_TOKEN + userId);
                }
            }
        }
        // 兼容：若仅有旧 token Cookie
        String legacy = CookieUtil.getCookieValue(request, "token");
        if (StrUtil.isNotEmpty(legacy)) {
            Object legacySession = redisUtils.get(Constants.REDIS_KEY_LOGIN_TOKEN + legacy);
            if (legacySession instanceof UserLoginDto) {
                UserLoginDto dto = (UserLoginDto) legacySession;
                redisUtils.delete(Constants.REDIS_KEY_LOGIN_TOKEN + legacy);
                if (dto.getUserId() != null) {
                    redisUtils.delete(Constants.REDIS_KEY_USER_TOKEN + dto.getUserId());
                }
            }
        }
        CookieUtil.clearUserAuthCookies(response);
    }

    public void invalidateByUserId(String userId) {
        if (StrUtil.isEmpty(userId)) {
            return;
        }
        Object refreshToken = redisUtils.get(Constants.REDIS_KEY_USER_REFRESH_TOKEN + userId);
        if (refreshToken != null) {
            redisUtils.delete(Constants.REDIS_KEY_REFRESH_TOKEN + refreshToken);
            redisUtils.delete(Constants.REDIS_KEY_USER_REFRESH_TOKEN + userId);
        }
        // 兼容旧会话
        Object legacyToken = redisUtils.get(Constants.REDIS_KEY_USER_TOKEN + userId);
        if (legacyToken != null) {
            redisUtils.delete(Constants.REDIS_KEY_LOGIN_TOKEN + legacyToken);
            redisUtils.delete(Constants.REDIS_KEY_USER_TOKEN + userId);
        }
    }

    /**
     * 解析当前用户；Access 过期且 Refresh 有效时静默续发 Access Cookie。
     */
    public UserLoginDto resolveUser(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = CookieUtil.getAccessToken(request);
        if (StrUtil.isEmpty(accessToken)) {
            // 兼容旧单令牌 Cookie
            return resolveLegacySession(request);
        }
        if (!jwtUtils.isSignatureValid(accessToken)) {
            return null;
        }
        UserLoginDto user = jwtUtils.parseUser(accessToken);
        if (user == null) {
            return null;
        }
        if (!jwtUtils.isExpired(accessToken)) {
            return user;
        }
        // Access 过期：用 Refresh 静默续期（不轮换 RT）
        if (response == null) {
            return null;
        }
        String refreshToken = CookieUtil.getRefreshToken(request);
        if (StrUtil.isEmpty(refreshToken)) {
            return null;
        }
        Object redisUserId = redisUtils.get(Constants.REDIS_KEY_REFRESH_TOKEN + refreshToken);
        if (redisUserId == null || !user.getUserId().equals(String.valueOf(redisUserId))) {
            return null;
        }
        reissueAccessToken(user, response);
        return user;
    }

    public UserLoginDto resolveUser(HttpServletRequest request) {
        HttpServletResponse response = currentResponse();
        return resolveUser(request, response);
    }

    private UserLoginDto resolveLegacySession(HttpServletRequest request) {
        String legacy = CookieUtil.getCookieValue(request, "token");
        if (StrUtil.isEmpty(legacy)) {
            return null;
        }
        Object value = redisUtils.get(Constants.REDIS_KEY_LOGIN_TOKEN + legacy);
        return value instanceof UserLoginDto ? (UserLoginDto) value : null;
    }

    private HttpServletResponse currentResponse() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getResponse();
    }
}

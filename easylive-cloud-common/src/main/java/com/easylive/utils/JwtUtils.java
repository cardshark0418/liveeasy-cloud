package com.easylive.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTValidator;
import com.easylive.config.AppConfig;
import com.easylive.entity.constants.Constants;
import com.easylive.entity.vo.UserLoginDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtUtils {

    @Resource
    private AppConfig appConfig;

    public String generateAccessToken(UserLoginDto userLoginDto) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userLoginDto.getUserId());
        payload.put("nickName", userLoginDto.getNickName());
        payload.put("avatar", userLoginDto.getAvatar());
        payload.put("exp", DateUtil.offsetSecond(new Date(), (int) Constants.JWT_ACCESS_EXPIRE_SECONDS));
        payload.put("iat", new Date());
        return JWTUtil.createToken(payload, getKeyBytes());
    }

    public boolean isSignatureValid(String token) {
        try {
            return JWT.of(token).setKey(getKeyBytes()).verify();
        } catch (Exception e) {
            log.warn("JWT signature invalid: {}", e.getMessage());
            return false;
        }
    }

    public boolean isExpired(String token) {
        try {
            JWTValidator.of(token).validateDate();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    public UserLoginDto parseUser(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            UserLoginDto dto = new UserLoginDto();
            dto.setUserId(asString(jwt.getPayload("userId")));
            dto.setNickName(asString(jwt.getPayload("nickName")));
            dto.setAvatar(asString(jwt.getPayload("avatar")));
            Object exp = jwt.getPayload("exp");
            if (exp instanceof Date) {
                dto.setExpireAt(((Date) exp).getTime());
            } else if (exp instanceof Number) {
                dto.setExpireAt(((Number) exp).longValue());
            }
            return dto.getUserId() == null ? null : dto;
        } catch (Exception e) {
            log.warn("JWT parse failed: {}", e.getMessage());
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private byte[] getKeyBytes() {
        return appConfig.getJwtSecret().getBytes(StandardCharsets.UTF_8);
    }
}

package com.easylive.utils;

import com.easylive.entity.constants.Constants;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CookieUtil {

    public static void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        Cookie cookie = new Cookie(Constants.COOKIE_ACCESS_TOKEN, accessToken);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(Constants.REFRESH_TOKEN_COOKIE_MAX_AGE);
        response.addCookie(cookie);
    }

    public static void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(Constants.COOKIE_REFRESH_TOKEN, refreshToken);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(Constants.REFRESH_TOKEN_COOKIE_MAX_AGE);
        response.addCookie(cookie);
    }

    public static void clearUserAuthCookies(HttpServletResponse response) {
        clearCookie(response, Constants.COOKIE_ACCESS_TOKEN);
        clearCookie(response, Constants.COOKIE_REFRESH_TOKEN);
        clearCookieCompatToken(response);
    }

    /** 清理旧版单令牌 Cookie `token` */
    public static void clearCookieCompatToken(HttpServletResponse response) {
        clearCookie(response, "token");
    }

    public static void adminSetToken2Cookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("adminToken", token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(3600 * 24 * 7);
        response.addCookie(cookie);
    }

    /** @deprecated 使用 {@link #getAccessToken(HttpServletRequest)} */
    @Deprecated
    public static void setToken2Cookie(HttpServletResponse response, String token) {
        setAccessTokenCookie(response, token);
    }

    public static String getAccessToken(HttpServletRequest request) {
        return getCookieValue(request, Constants.COOKIE_ACCESS_TOKEN);
    }

    public static String getRefreshToken(HttpServletRequest request) {
        return getCookieValue(request, Constants.COOKIE_REFRESH_TOKEN);
    }

    /** @deprecated 兼容旧名，优先读 accessToken，其次旧 token */
    @Deprecated
    public static String getCookieToken(HttpServletRequest request) {
        String accessToken = getAccessToken(request);
        if (accessToken != null) {
            return accessToken;
        }
        return getCookieValue(request, "token");
    }

    public static String adminGetCookieToken(HttpServletRequest request) {
        return getCookieValue(request, "adminToken");
    }

    private static void clearCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    public static String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

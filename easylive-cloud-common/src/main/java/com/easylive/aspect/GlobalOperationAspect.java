package com.easylive.aspect;

import cn.hutool.core.util.StrUtil;
import com.easylive.annotation.GlobalInterceptor;
import com.easylive.auth.UserAuthComponent;
import com.easylive.entity.vo.UserLoginDto;
import com.easylive.enums.ResponseCodeEnum;
import com.easylive.exception.BusinessException;
import com.easylive.redis.RedisUtils;
import com.easylive.utils.CookieUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

@Component("operationAspect")
@Aspect
@Slf4j
public class GlobalOperationAspect {

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private UserAuthComponent userAuthComponent;

    @Before("@annotation(com.easylive.annotation.GlobalInterceptor)")
    public void interceptorDo(JoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        GlobalInterceptor interceptor = method.getAnnotation(GlobalInterceptor.class);
        if (null == interceptor) {
            return;
        }
        if (interceptor.checkLogin()) {
            checkLogin();
        }
    }

    private void checkLogin() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        UserLoginDto tokenUserInfoDto = userAuthComponent.resolveUser(request, response);
        if (tokenUserInfoDto != null) {
            return;
        }
        // 管理端上传等场景：允许 adminToken
        String adminToken = CookieUtil.adminGetCookieToken(request);
        if (!StrUtil.isEmpty(adminToken)) {
            Object admin = redisUtils.get(com.easylive.entity.constants.Constants.REDIS_KEY_ADMIN_TOKEN + adminToken);
            if (admin != null) {
                return;
            }
        }
        throw new BusinessException(ResponseCodeEnum.CODE_901);
    }
}

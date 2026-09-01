package com.easylive.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.easylive.annotation.GlobalInterceptor;
import com.easylive.auth.UserAuthComponent;
import com.easylive.entity.constants.Constants;
import com.easylive.entity.po.UserFocus;
import com.easylive.entity.po.UserInfo;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.entity.vo.UserCountInfoDto;
import com.easylive.entity.vo.UserLoginDto;
import com.easylive.entity.vo.UserRegisterRequest;
import com.easylive.exception.BusinessException;
import com.easylive.redis.RedisComponent;
import com.easylive.redis.RedisUtils;
import com.easylive.service.UserFocusService;
import com.easylive.service.UserInfoService;
import com.wf.captcha.ArithmeticCaptcha;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.easylive.entity.vo.ResponseVO.getSuccessResponseVO;

@Validated
@RestController
@RequestMapping("/account")
public class AccountController {
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private RedisComponent redisComponent;
    @Autowired
    private UserFocusService userFocusService;
    @Autowired
    private UserAuthComponent userAuthComponent;

    @Autowired
    private UserInfoService userInfoService;
    @RequestMapping("/checkCode")
    public ResponseVO checkCode(){
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(100,42);
        String ans = captcha.text();
        String checkCodeKey = UUID.randomUUID().toString();
        redisUtils.setex(Constants.REDIS_KEY_CHECK_CODE+checkCodeKey,ans,Constants.ONE_MIN_MILLS*5);
        String checkCodeBase64 = captcha.toBase64();
        Map<String,String> result = new HashMap<>();
        result.put("checkCode",checkCodeBase64);
        result.put("checkCodeKey",checkCodeKey);
        return getSuccessResponseVO(result);
    }

    @RequestMapping("register")
    public ResponseVO register(UserRegisterRequest registerRequest){
            try {
                if(!registerRequest.getCheckCode().equals(redisUtils.get(Constants.REDIS_KEY_CHECK_CODE+registerRequest.getCheckCodeKey()))){
                    throw new BusinessException("验证码错误！");
                }
                userInfoService.register(registerRequest.getEmail(),registerRequest.getNickName(),registerRequest.getRegisterPassword());
                return ResponseVO.getSuccessResponseVO(null);
            }
            finally {
                redisUtils.delete(Constants.REDIS_KEY_CHECK_CODE+registerRequest.getCheckCodeKey());
            }
    }

    @RequestMapping("login")
    public ResponseVO login(@NotEmpty @Email String email,
                            @NotEmpty String password,
                            String checkCode,
                            String checkCodeKey,
                            HttpServletResponse response,
                            HttpServletRequest request){
        try {
            if(StrUtil.isBlank(checkCode) || !checkCode.equals(redisUtils.get(Constants.REDIS_KEY_CHECK_CODE+checkCodeKey))){
                throw new BusinessException("验证码错误！");
            }
            String ip = ServletUtil.getClientIP(request);
            UserLoginDto userLoginDto = userInfoService.login(email,password,ip,response);
            return ResponseVO.getSuccessResponseVO(userLoginDto);
        }
        finally {
            if(checkCodeKey!=null){
                redisUtils.delete(Constants.REDIS_KEY_CHECK_CODE+checkCodeKey);
            }
        }
    }

    /**
     * 页面初始化拉取登录态（Cookie 自动携带；Access 过期时服务端静默续期）。
     * 替代旧 autoLogin。
     */
    @RequestMapping("getLoginInfo")
    public ResponseVO getLoginInfo(HttpServletResponse response, HttpServletRequest request){
        UserLoginDto userLoginDto = userAuthComponent.resolveUser(request, response);
        return getSuccessResponseVO(userLoginDto);
    }

    @RequestMapping("/logout")
    public ResponseVO logout(HttpServletRequest request,HttpServletResponse response){
        userAuthComponent.logout(request, response);
        return ResponseVO.getSuccessResponseVO(null);
    }

    @RequestMapping(value = "/getUserCountInfo")
    @GlobalInterceptor(checkLogin = true)
    public ResponseVO getUserCountInfo(HttpServletRequest request) {
        UserLoginDto tokenUserInfoDto = redisComponent.getTokenUserInfoDto(request);
        UserInfo userInfo = userInfoService.getById(tokenUserInfoDto.getUserId());
        long focus = userFocusService.count(new LambdaQueryWrapper<UserFocus>().eq(UserFocus::getUserId, tokenUserInfoDto.getUserId()));
        long fans = userFocusService.count(new LambdaQueryWrapper<UserFocus>().eq(UserFocus::getFocusUserId, tokenUserInfoDto.getUserId()));
        UserCountInfoDto userCountInfoDto = new UserCountInfoDto();
        userCountInfoDto.setFocusCount((int) focus);
        userCountInfoDto.setFansCount((int) fans);
        userCountInfoDto.setCurrentCoinCount(userInfo.getCurrentCoinCount());
        return getSuccessResponseVO(userCountInfoDto);
    }

}

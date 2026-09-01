package com.easylive.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.easylive.auth.UserAuthComponent;
import com.easylive.entity.constants.Constants;
import com.easylive.entity.po.UserFocus;
import com.easylive.entity.po.UserInfo;
import com.easylive.entity.po.VideoInfo;
import com.easylive.entity.vo.CountInfoDto;
import com.easylive.entity.vo.UserLoginDto;
import com.easylive.enums.ResponseCodeEnum;
import com.easylive.exception.BusinessException;
import com.easylive.mapper.UserFocusMapper;
import com.easylive.mapper.UserInfoMapper;
import com.easylive.mapper.VideoInfoMapper;
import com.easylive.redis.RedisComponent;
import com.easylive.service.UserInfoService;
import com.github.yulichang.base.MPJBaseServiceImpl;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("userInfoService")
public class UserInfoServiceImpl extends MPJBaseServiceImpl<UserInfoMapper,UserInfo> implements UserInfoService{
    @Autowired
    private UserInfoMapper userInfoMapper;
    @Autowired
    private VideoInfoMapper videoInfoMapper;
    @Autowired
    private UserFocusMapper userFocusMapper;
    @Autowired
    private RedisComponent redisComponent;
    @Autowired
    private UserAuthComponent userAuthComponent;

    @Override
    public void register(String email, String nickName, String password) {
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getEmail,email);
        long count = userInfoMapper.selectCount(wrapper);
        if(count>0){
            throw new BusinessException("该邮件已被注册");
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setEmail(email);
        userInfo.setNickName(nickName);
        String md5Password = DigestUtils.md5DigestAsHex(password.getBytes());
        userInfo.setPassword(md5Password);
        userInfo.setSex(2);
        userInfo.setJoinTime(LocalDateTime.now());
        userInfo.setTheme(1);
        userInfo.setCurrentCoinCount(10);
        userInfo.setTotalCoinCount(10);
        userInfoMapper.insert(userInfo);
    }

    @Override
    public UserLoginDto login(String email, String password, String ip, HttpServletResponse response) {
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getEmail,email);
        UserInfo userInfo = userInfoMapper.selectOne(wrapper);
        // 前端登录已 MD5 一次；注册时后端对明文 MD5 入库，此处直接比对，勿再哈希
        if(userInfo==null || !userInfo.getPassword().equals(password)){
            throw new BusinessException("邮箱或密码错误");
        }
        if(userInfo.getStatus()==0){
            throw new BusinessException("账号已封禁");
        }
        //更新ip和登录时间
        UserInfo updateInfo = new UserInfo();
        updateInfo.setLastLoginIp(ip);
        updateInfo.setLastLoginTime(LocalDateTime.now());
        userInfoMapper.update(updateInfo,wrapper);
        //构造dto对象
        UserLoginDto userLoginDto = BeanUtil.copyProperties(userInfo, UserLoginDto.class);
        userLoginDto.setStatus(1);
        userAuthComponent.issueTokens(userLoginDto, response);
        return userLoginDto;
    }

    @Override
    public UserInfo getUserDetailInfo(String currentUserId, String userId) {
        UserInfo userInfo = getById(userId);
        if (null == userInfo) {
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
        CountInfoDto countInfoDto = videoInfoMapper.selectJoinOne(CountInfoDto.class,
                new MPJLambdaWrapper<VideoInfo>()
                        .selectSum(VideoInfo::getPlayCount, CountInfoDto::getPlayCount)
                        .selectSum(VideoInfo::getLikeCount, CountInfoDto::getLikeCount)
                        .eq(VideoInfo::getUserId, userId));
        BeanUtil.copyProperties(countInfoDto, userInfo);

        Integer fansCount = Math.toIntExact(userFocusMapper.selectCount(new LambdaQueryWrapper<UserFocus>().eq(UserFocus::getFocusUserId, userId)));
        Integer focusCount = Math.toIntExact(userFocusMapper.selectCount(new LambdaQueryWrapper<UserFocus>().eq(UserFocus::getUserId, userId)));
        userInfo.setFansCount(fansCount);
        userInfo.setFocusCount(focusCount);

        if (currentUserId == null) {
            userInfo.setHaveFocus(false);
        } else {
            Boolean userFocus = userFocusMapper.exists(new LambdaQueryWrapper<UserFocus>()
                    .eq(UserFocus::getFocusUserId,userId)
                    .eq(UserFocus::getUserId,currentUserId));
            userInfo.setHaveFocus(userFocus);
        }
        return userInfo;
    }

    @Override
    @GlobalTransactional
    public void updateUserInfo(UserInfo userInfo, UserLoginDto tokenUserInfoDto) {
        UserInfo dbInfo = this.userInfoMapper.selectById(userInfo.getUserId());
        if (!dbInfo.getNickName().equals(userInfo.getNickName()) && dbInfo.getCurrentCoinCount() < Constants.UPDATE_NICK_NAME_COIN) {
            throw new BusinessException("硬币不足，无法修改昵称");
        }
        if (!dbInfo.getNickName().equals(userInfo.getNickName())) {
            Integer count = this.userInfoMapper.update(null,new LambdaUpdateWrapper<UserInfo>()
                    .eq(UserInfo::getUserId,userInfo.getUserId())
                    .ge(UserInfo::getCurrentCoinCount, Constants.UPDATE_NICK_NAME_COIN)
                    .setSql("current_coin_count = current_coin_count - " + Constants.UPDATE_NICK_NAME_COIN));
            if (count == 0) {
                throw new BusinessException("硬币不足，无法修改昵称");
            }
        }
        this.userInfoMapper.updateById(userInfo);

        Boolean updateTokenInfo = false;
        if (!Objects.equals(userInfo.getAvatar(), tokenUserInfoDto.getAvatar())) {
            tokenUserInfoDto.setAvatar(userInfo.getAvatar());
            updateTokenInfo = true;
        }
        if (!tokenUserInfoDto.getNickName().equals(userInfo.getNickName())) {
            tokenUserInfoDto.setNickName(userInfo.getNickName());
            updateTokenInfo = true;
        }
        if (updateTokenInfo) {
            redisComponent.updateTokenInfo(tokenUserInfoDto);
        }
    }


    @Override
    public Map<String, UserInfo> getUserInfoBatch(Collection<String> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return new HashMap<>();
        }

        // 1. 直接查库：SELECT * FROM user_info WHERE user_id IN (...)
        List<UserInfo> list = this.listByIds(userIds);

        // 2. 将结果 List 转为 Map，方便调用方根据 ID 获取对象
        // 使用 (oldValue, newValue) -> newValue 处理可能的重复数据冲突
        return list.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        UserInfo::getUserId,
                        Function.identity(),
                        (v1, v2) -> v1
                ));
    }

}

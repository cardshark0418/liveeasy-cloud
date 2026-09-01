package com.easylive.redis;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.easylive.auth.UserAuthComponent;
import com.easylive.config.AppConfig;
import com.easylive.entity.constants.Constants;
import com.easylive.entity.po.VideoInfoFilePost;
//import com.easylive.entity.vo.SysSettingDto;
//import com.easylive.entity.vo.UploadingFileDto;
//import com.easylive.entity.vo.UserLoginDto;
//import com.easylive.entity.vo.VideoPlayInfoDto;
import com.easylive.entity.vo.SysSettingDto;
import com.easylive.entity.vo.UploadingFileDto;
import com.easylive.entity.vo.UserLoginDto;
import com.easylive.entity.vo.VideoPlayInfoDto;
import com.easylive.enums.DateTimePatternEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class RedisComponent {
    @Resource
    private RedisUtils redisUtils;

    @Resource
    private AppConfig appConfig;

    @Resource
    private UserAuthComponent userAuthComponent;

    public String savePreVideoFileInfo(String userId,String fileName,Integer chunks){
        String uploadId = RandomUtil.randomString(10);
        UploadingFileDto fileDto = new UploadingFileDto();
        fileDto.setChunks(chunks);
        fileDto.setFileName(fileName);
        fileDto.setUploadId(uploadId);
        fileDto.setChunkIndex(0);

        String day = DateUtil.format(new Date(), DateTimePatternEnum.YYYYMMDD.getPattern());
        String filePath = day + "/" + uploadId;

        String folder = appConfig.getProjectFolder() + "file/" + "temp/" + filePath;
        File folderFile = new File(folder);
        if (!folderFile.exists()) {
            folderFile.mkdirs();
        }
        fileDto.setFilePath(filePath);
        redisUtils.setex(Constants.REDIS_KEY_UPLOADING_FILE + userId + uploadId, fileDto, Constants.ONE_MIN_MILLS*60*24);
        return uploadId;
    }

    public UploadingFileDto getUploadingVideoFile(String userId, String uploadId) {
        return (UploadingFileDto) redisUtils.get(Constants.REDIS_KEY_UPLOADING_FILE + userId + uploadId);
    }

    public SysSettingDto getSysSettingDto() {
        SysSettingDto sysSettingDto = (SysSettingDto) redisUtils.get(Constants.REDIS_KEY_SYS_SETTING);
        if (sysSettingDto == null) {
            sysSettingDto = new SysSettingDto();
        }
        return sysSettingDto;
    }

    public void updateVideoFileInfo(String userId, UploadingFileDto uploadingFileDto) {
        redisUtils.setex(Constants.REDIS_KEY_UPLOADING_FILE + userId + uploadingFileDto.getUploadId(), uploadingFileDto, Constants.ONE_MIN_MILLS*60*24);
    }

    public void delVideoFileInfo(String userId, String uploadId) {
        redisUtils.delete(Constants.REDIS_KEY_UPLOADING_FILE + userId + uploadId);
    }

    public void addFile2DelQueue(String videoId, List<String> delFilePathList) {
        if (delFilePathList == null || delFilePathList.isEmpty()) {
            return;
        }
        redisUtils.lpushAll(Constants.REDIS_KEY_FILE_DEL + videoId, new ArrayList<>(delFilePathList), Constants.ONE_MIN_MILLS * 60 * 24);
    }

    public void addFile2TransferQueue(List<VideoInfoFilePost> fileList) {
        if (CollectionUtils.isEmpty(fileList)) {
            return;
        }
        // 循环放入，这样队列里存储的就是一个个独立的对象
        for (VideoInfoFilePost file : fileList) {
            redisUtils.lpush(Constants.REDIS_KEY_QUEUE_TRANSFER, file,(long)-1);
        }
    }

    public List<String> getDelFileList(String videoId) {
        List<String> filePathList = redisUtils.getQueueList(Constants.REDIS_KEY_FILE_DEL + videoId);
        return filePathList;
    }

    public void cleanDelFileList(String videoId) {
        redisUtils.delete(Constants.REDIS_KEY_FILE_DEL + videoId);
    }

    public void addVideoPlay(VideoPlayInfoDto videoPlayInfoDto) {
        redisUtils.lpush(Constants.REDIS_KEY_QUEUE_VIDEO_PLAY, videoPlayInfoDto, null);
    }

    public UserLoginDto getTokenUserInfoDto(HttpServletRequest request){
        return userAuthComponent.resolveUser(request);
    }

    public Integer reportVideoPlayOnline(String fileId, String deviceId) {
        String userPlayOnlineKey = String.format(Constants.REDIS_KEY_VIDEO_PLAY_COUNT_USER, fileId, deviceId);
        String playOnlineCountKey = String.format(Constants.REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE, fileId);

        if (!redisUtils.keyExists(userPlayOnlineKey)) {//如果redis中这个用户对这个视频没有观看状态
            redisUtils.setex(userPlayOnlineKey, fileId, Constants.REDIS_KEY_EXPIRES_ONE_SECONDS * 8);
            return redisUtils.incrementex(playOnlineCountKey, Constants.REDIS_KEY_EXPIRES_ONE_SECONDS * 10).intValue();
        }
        //给视频在线总数量续期
        redisUtils.expire(playOnlineCountKey, Constants.REDIS_KEY_EXPIRES_ONE_SECONDS * 10);
        //给播放用户续期
        redisUtils.expire(userPlayOnlineKey, Constants.REDIS_KEY_EXPIRES_ONE_SECONDS * 8);
        Integer count = (Integer) redisUtils.get(playOnlineCountKey);
        return count == null ? 1 : count;
    }

    public void updateTokenInfo(UserLoginDto tokenUserInfoDto) {
        HttpServletResponse response = currentResponse();
        if (response != null) {
            userAuthComponent.reissueAccessToken(tokenUserInfoDto, response);
        }
    }

    private HttpServletResponse currentResponse() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getResponse();
    }

    public void addKeywordCount(String keyword) {
        redisUtils.zaddCount(Constants.REDIS_KEY_VIDEO_SEARCH_COUNT, keyword);
    }

    public List<Object> getKeywordTop(Integer top) {
        return redisUtils.getZSetList(Constants.REDIS_KEY_VIDEO_SEARCH_COUNT, top - 1);
    }

    public void recordVideoPlayCount(String videoId) {
        String date = DateUtil.format(new Date(), DateTimePatternEnum.YYYYMMDD.getPattern());
        redisUtils.incrementex(Constants.REDIS_KEY_VIDEO_PLAY_COUNT + date + ":" + videoId, Constants.REDIS_KEY_EXPIRES_DAY * 2L);
    }

    public Map<String, Object> getVideoPlayCount(String date) {
        return redisUtils.getBatch(Constants.REDIS_KEY_VIDEO_PLAY_COUNT + date);
    }

    public void saveSettingDto(SysSettingDto sysSettingDto) {
        redisUtils.set(Constants.REDIS_KEY_SYS_SETTING, sysSettingDto);
    }
}
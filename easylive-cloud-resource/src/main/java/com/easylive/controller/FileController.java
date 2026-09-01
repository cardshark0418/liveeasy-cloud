package com.easylive.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import com.easylive.annotation.GlobalInterceptor;
import com.easylive.api.comsumer.VideoClient;
import com.easylive.config.AppConfig;
import com.easylive.entity.constants.Constants;
import com.easylive.entity.po.VideoInfoFile;
import com.easylive.entity.vo.*;
import com.easylive.enums.DateTimePatternEnum;
import com.easylive.enums.ResponseCodeEnum;
import com.easylive.exception.BusinessException;
import com.easylive.redis.RedisComponent;
import com.easylive.utils.CookieUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

import static com.easylive.entity.vo.ResponseVO.getSuccessResponseVO;

@Validated
@Slf4j
@RestController
public class FileController {
    @Autowired
    private AppConfig appConfig;
    @Autowired
    private RedisComponent redisComponent;
    @Autowired
    private VideoClient videoClient;
    @RequestMapping("/getResource")
    public void getResource(HttpServletResponse response, @NotEmpty String sourceName) {
        // 1. 安全校验：检查路径是否合法（防止 ../../ 攻击）
        if (StrUtil.contains(sourceName, "..") || StrUtil.isBlank(sourceName)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        // 2. 拼接绝对路径
        String fullPath = appConfig.getProjectFolder() + "file/" + sourceName;
        File file = FileUtil.file(fullPath);
        log.info(fullPath);
        // 3. 检查文件是否存在
        if (!FileUtil.exist(file)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        // 4. 设置缓存（30天）
        response.setHeader("Cache-Control", "max-age=2592000");

        // 5. 一行代码完成：读取文件、判断 Content-Type、写入响应流
        ServletUtil.write(response, file);
    }

    // 预上传
    @GlobalInterceptor(checkLogin = true)
    @RequestMapping("/preUploadVideo")
    public ResponseVO preUploadVideo(@NotEmpty String fileName, @NotNull Integer chunks, HttpServletRequest request) {
        UserLoginDto userLoginDto = redisComponent.getTokenUserInfoDto(request);
        String uploadId = redisComponent.savePreVideoFileInfo(userLoginDto.getUserId(), fileName, chunks);
        return getSuccessResponseVO(uploadId);
    }

    @RequestMapping("/uploadVideo")
    @GlobalInterceptor(checkLogin = true)
    public ResponseVO uploadVideo(@NotNull MultipartFile chunkFile,@NotNull Integer chunkIndex, @NotEmpty String uploadId,HttpServletRequest request) throws IOException {
        UserLoginDto userLoginDto = redisComponent.getTokenUserInfoDto(request);
        UploadingFileDto uploadingFileDto = redisComponent.getUploadingVideoFile(userLoginDto.getUserId(), uploadId);
        if(uploadingFileDto==null){
            throw new BusinessException("文件不存在,请重新上传！");
        }
        SysSettingDto sysSettingDto = redisComponent.getSysSettingDto();
        if(uploadingFileDto.getFileSize()>sysSettingDto.getVideoSize()*1024*1024){
            throw new BusinessException("文件大小超出限制！");
        }
        //判断分片
        if ((chunkIndex - 1) > uploadingFileDto.getChunkIndex()  || chunkIndex > uploadingFileDto.getChunks() - 1) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        String folder = appConfig.getProjectFolder() + "file/temp/" + uploadingFileDto.getFilePath();
        File targetFile = new File(folder + "/" + chunkIndex);
        chunkFile.transferTo(targetFile);
        //记录文件上传的分片数
        uploadingFileDto.setChunkIndex(chunkIndex);
        uploadingFileDto.setFileSize(uploadingFileDto.getFileSize() + chunkFile.getSize());
        redisComponent.updateVideoFileInfo(userLoginDto.getUserId(), uploadingFileDto);
        return getSuccessResponseVO(null);
    }

    @RequestMapping("/delUploadVideo")
    @GlobalInterceptor(checkLogin = true)
    public ResponseVO delUploadVideo(@NotEmpty String uploadId ,HttpServletRequest request) throws IOException {
        //获取用户id
        UserLoginDto tokenUserInfoDto = redisComponent.getTokenUserInfoDto(request);
        //获取文件dto对象
        UploadingFileDto fileDto = redisComponent.getUploadingVideoFile(tokenUserInfoDto.getUserId(), uploadId);
        if (fileDto == null) {
            throw new BusinessException("文件不存在请重新上传");
        }
        //删除文件
        redisComponent.delVideoFileInfo(tokenUserInfoDto.getUserId(), uploadId);
        FileUtils.deleteDirectory(new File(appConfig.getProjectFolder() + "file/temp/" + fileDto.getFilePath()));
        return getSuccessResponseVO(uploadId);
    }

    @RequestMapping("/uploadImage")
    @GlobalInterceptor(checkLogin = true)
    public ResponseVO uploadCover(@NotNull MultipartFile file, @NotNull Boolean createThumbnail) throws IOException {
        String month = DateUtil.format(new Date(), DateTimePatternEnum.YYYYMM.getPattern());
        String folder = appConfig.getProjectFolder() + "file/cover/" + month;
        File folderFile = new File(folder);
        //创建文件夹
        if (!folderFile.exists()) {
            folderFile.mkdirs();
        }
        String fileName = file.getOriginalFilename();
        String fileSuffix = fileName.substring(fileName.lastIndexOf("."));
        String realFileName = RandomUtil.randomString(20) + fileSuffix;
        String filePath = folder + "/" + realFileName;
        file.transferTo(new File(filePath));
        if (createThumbnail) {
            //生成缩略图
            String thumbPath = filePath + Constants.IMAGE_THUMBNAIL_SUFFIX;
            ImgUtil.scale(
                    FileUtil.file(filePath),
                    FileUtil.file(thumbPath),
                    200,
                    -1,
                    null
            );
        }
        return getSuccessResponseVO("cover/" + month + "/" + realFileName);
    }

    @RequestMapping("/videoResource/{fileId}")
    @GlobalInterceptor
    public void getVideoResource(HttpServletResponse response,HttpServletRequest request, @PathVariable @NotEmpty String fileId) {
        VideoInfoFile videoInfoFile = videoClient.getVideoInfoFileByFileId(fileId);
        if (videoInfoFile == null) {
            response.setStatus(404);
            return;
        }
        String filePath = videoInfoFile.getFilePath();
        readFile(response, filePath + "/" + Constants.M3U8_NAME);

        VideoPlayInfoDto videoPlayInfoDto = new VideoPlayInfoDto();
        videoPlayInfoDto.setVideoId(videoInfoFile.getVideoId());
        videoPlayInfoDto.setFileIndex(videoInfoFile.getFileIndex());

        String token = CookieUtil.getAccessToken(request);
        if (StrUtil.isEmpty(token)) {
            token = CookieUtil.getCookieValue(request, "token");
        }
        if (token != null) {
            UserLoginDto tokenUserInfoDto = redisComponent.getTokenUserInfoDto(request);
            if (tokenUserInfoDto != null) {
                videoPlayInfoDto.setUserId(tokenUserInfoDto.getUserId());
            }
        }
        redisComponent.addVideoPlay(videoPlayInfoDto);
    }

    @RequestMapping("/videoResource/{fileId}/{ts}")
    @GlobalInterceptor
    public void getVideoResourceTs(HttpServletResponse response, @PathVariable @NotEmpty String fileId, @PathVariable @NotNull String ts) {
        VideoInfoFile videoInfoFile = videoClient.getVideoInfoFileByFileId(fileId);
        if (videoInfoFile == null) {
            response.setStatus(404);
            return;
        }
        String filePath = videoInfoFile.getFilePath();
        readFile(response, filePath + "/" + ts);
    }

    protected void readFile(HttpServletResponse response, String filePath) {
        File file = new File(appConfig.getProjectFolder() + "file/" + filePath);
        if (!file.exists()) {
            return;
        }
        try (OutputStream out = response.getOutputStream(); FileInputStream in = new FileInputStream(file)) {
            byte[] byteData = new byte[1024];
            int len = 0;
            while ((len = in.read(byteData)) != -1) {
                out.write(byteData, 0, len);
            }
            out.flush();
        } catch (Exception e) {
            log.error("读取文件异常", e);
        }
    }
}

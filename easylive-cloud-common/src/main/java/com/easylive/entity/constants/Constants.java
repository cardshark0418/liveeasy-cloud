package com.easylive.entity.constants;

public class Constants {
    public static final String REDIS_KEY_PREFIX = "easylive:";
    public static final String REDIS_KEY_CHECK_CODE = REDIS_KEY_PREFIX+"checkcode:";
    public static final Integer ONE_MIN_MILLS = 60000;
    public static final String PASSWORD_REGEXP = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,18}$";
    public static final String REDIS_KEY_LOGIN_TOKEN = REDIS_KEY_PREFIX+"token:";
    /** Refresh Token：easylive:rt:{uuid} -> userId */
    public static final String REDIS_KEY_REFRESH_TOKEN = REDIS_KEY_PREFIX + "rt:";
    /** 用户当前 RT：easylive:user:rt:{userId} -> uuid（单端登录） */
    public static final String REDIS_KEY_USER_REFRESH_TOKEN = REDIS_KEY_PREFIX + "user:rt:";
    public static final String COOKIE_ACCESS_TOKEN = "accessToken";
    public static final String COOKIE_REFRESH_TOKEN = "refreshToken";
    /** Access Token（JWT）有效期：30 分钟 */
    public static final long JWT_ACCESS_EXPIRE_SECONDS = 30 * 60L;
    /** Refresh Token 有效期：9 天（毫秒，配合 RedisUtils.setex） */
    public static final long REFRESH_TOKEN_EXPIRE_MS = ONE_MIN_MILLS * 60L * 24 * 9;
    public static final int REFRESH_TOKEN_COOKIE_MAX_AGE = 60 * 60 * 24 * 9;
    public static final String REDIS_KEY_ADMIN_TOKEN = REDIS_KEY_PREFIX+"admin:token:";
    public static final String REDIS_KEY_CATEGORY_LIST = REDIS_KEY_PREFIX+"category:list";
    public static final String IMAGE_THUMBNAIL_SUFFIX = "_thumbnail.jpg";
    public static final String REDIS_KEY_UPLOADING_FILE = REDIS_KEY_PREFIX+"uploading:";
    public static final String REDIS_KEY_SYS_SETTING = REDIS_KEY_PREFIX+"sysSetting:";
    public static final String REDIS_KEY_FILE_DEL = REDIS_KEY_PREFIX+"file:list:del:";
    public static final String REDIS_KEY_QUEUE_TRANSFER = REDIS_KEY_PREFIX+"file:queue:transfer:";
    public static final String TEMP_VIDEO_NAME = "/temp.mp4";
    public static final String TS_NAME = "index.ts";
    public static final String M3U8_NAME = "index.m3u8";
    public static final String REDIS_KEY_VIDEO_PLAY_COUNT = REDIS_KEY_PREFIX + "video:playcount:";


    public static final String REDIS_KEY_VIDEO_SEARCH_COUNT = REDIS_KEY_PREFIX + "video:search:";

    public static final String TEMP_COVER_NAME = "/cover.jpg";

    public static final Integer UPDATE_NICK_NAME_COIN = 5;

    public static final String  VIDEO_CODE_HEVC= "hevc";

    public static final String  VIDEO_CODE_TEMP_FILE_SUFFIX= "_temp";
    public static final Integer  PAGE_SIZE_15 = 15;
    public static final String REDIS_KEY_QUEUE_VIDEO_PLAY = REDIS_KEY_PREFIX + "queue:video:play:";

    public static final String REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE_PREIFX = REDIS_KEY_PREFIX + "video:play:online:";

    public static final String REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE = REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE_PREIFX + "count:%s";

    public static final String REDIS_KEY_VIDEO_PLAY_COUNT_USER_PREFIX = "user:";

    public static final String REDIS_KEY_VIDEO_PLAY_COUNT_USER = REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE_PREIFX + REDIS_KEY_VIDEO_PLAY_COUNT_USER_PREFIX + "%s:%s";
    public static final Integer REDIS_KEY_EXPIRES_ONE_SECONDS = 1000;



    public static final Integer REDIS_KEY_EXPIRES_DAY = ONE_MIN_MILLS * 60 * 24;

    public static final String REDIS_KEY_USER_TOKEN = REDIS_KEY_PREFIX+"user:token:";
    public static final String INNER_API_PREFIX = "/innerApi";
    public static final String SERVER_NAME_WEB = "easylive-cloud-web";
    public static final String SERVER_NAME_RESOURCE = "easylive-cloud-resource";
    public static final String SERVER_NAME_INTERACT = "easylive-cloud-interact";
}

package com.sun.comment.common;

/**
 * @author sun
 */
public interface RedisConstants {
    Long TTL_TWO = 2L;
    Long TTL_TEN = 10L;
    Long TTL_THIRTY = 30L;

    String LOGIN_CAPTCHA_KEY = "login:captcha:";
    String LOGIN_USER_KEY = "login:token:";

    String CACHE_SHOP_KEY = "cache:shop:";
    String CACHE_SHOP_TYPE_KEY = "cache:shop:type";

    String LOCK_SHOP_KEY = "lock:shop:";
    String LOCK_ORDER_KEY = "lock:order:";

    String SECKILL_STOCK_KEY = "seckill:stock:";
    String BLOG_LIKED_KEY = "blog:liked:";
    String FEED_KEY = "feed:";
    String SHOP_GEO_KEY = "shop:geo:";
    String USER_SIGN_KEY = "sign:";
}

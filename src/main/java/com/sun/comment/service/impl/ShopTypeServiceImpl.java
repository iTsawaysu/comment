package com.sun.comment.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.common.CommonResult;
import com.sun.comment.common.ErrorCode;
import com.sun.comment.common.exception.ThrowUtils;
import com.sun.comment.entity.ShopType;
import com.sun.comment.mapper.ShopTypeMapper;
import com.sun.comment.service.ShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.sun.comment.common.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.sun.comment.common.RedisConstants.TTL_TWO;

/**
 * @author sun
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements ShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 展示商铺类型（缓存）
     */
    @Override
    public CommonResult<List<ShopType>> getShopTypeList() {
        String json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        List<ShopType> shopTypeList;
        if (StrUtil.isNotBlank(json)) {
            shopTypeList = JSONUtil.toList(JSONUtil.parseArray(json), ShopType.class);
            ThrowUtils.throwIf(CollectionUtil.isEmpty(shopTypeList), ErrorCode.NOT_FOUND_ERROR, "商铺类型列表不存在");
            return CommonResult.success(shopTypeList);
        }
        shopTypeList = this.lambdaQuery().orderByAsc(ShopType::getSort).list();
        ThrowUtils.throwIf(CollectionUtil.isEmpty(shopTypeList), ErrorCode.NOT_FOUND_ERROR, "商铺类型列表不存在");
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopTypeList), TTL_TWO, TimeUnit.HOURS);
        return CommonResult.success(shopTypeList);
    }
}

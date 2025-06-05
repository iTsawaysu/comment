package com.sun.comment.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sun.comment.common.Result;
import com.sun.comment.common.ReturnCode;
import com.sun.comment.common.SystemConstants;
import com.sun.comment.entity.Shop;
import com.sun.comment.common.exception.ThrowUtils;
import com.sun.comment.service.ShopService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author sun
 */
@RestController
@RequestMapping("/shop")
public class ShopController {
    @Resource
    public ShopService shopService;

    /**
     * 根据 id 查询商铺信息（添加缓存并解决缓存穿透、缓存雪崩、缓存击穿问题）
     *
     * @param id 商铺 id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result<Shop> getShopById(@PathVariable("id") Long id) {
        return shopService.getShopById(id);
    }

    /**
     * 新增商铺信息
     *
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result<Long> saveShop(@RequestBody Shop shop) {
        ThrowUtils.throwIf(shop == null, ReturnCode.PARAM_ERROR);
        boolean result = shopService.save(shop);
        ThrowUtils.throwIf(!result, ReturnCode.OPERATION_ERROR);
        return Result.success(shop.getId());
    }

    /**
     * 更新商铺信息（先操作数据库，后删除缓存）
     *
     * @param shop 商铺数据
     */
    @PutMapping
    public Result<String> update(@RequestBody Shop shop) {
        return shopService.update(shop);
    }

    /**
     * 根据店铺类型分页查询店铺信息（按照距离排序）
     * @param typeId  店铺类型
     * @param current 当前页码
     * @param x       经度
     * @param y       纬度
     * @return 店铺列表
     */
    @GetMapping("/of/type")
    public Result<List<Shop>> getShopsByTypeOrderByDistance(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y) {
        return shopService.getShopsByTypeOrderByDistance(typeId, current, x, y);
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     *
     * @param name    商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result<List<Shop>> queryShopByName(@RequestParam(value = "name", required = false) String name,
                                                    @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Shop> pageInfo = new Page<>(current, SystemConstants.MAX_PAGE_SIZE);
        Page<Shop> shopPage = shopService.lambdaQuery()
                .like(StrUtil.isNotBlank(name), Shop::getName, name)
                .page(pageInfo);
        return Result.success(shopPage.getRecords());
    }
}

package com.sun.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sun.dto.Result;
import com.sun.entity.Shop;
import com.sun.service.ShopService;
import com.sun.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author sun
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public ShopService shopService;

    /**
     * 根据id查询店铺信息
     * @param id 店铺id
     * @return 店铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增店铺信息
     * @param shop 店铺数据
     * @return 店铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新店铺信息
     * @param shop 店铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        return shopService.modify(shop);
    }

    /**
     * 根据店铺类型分页查询店铺信息（按照距离排序）
     * @param typeId 店铺类型
     * @param current 当前页码
     * @param x 经度
     * @param y 纬度
     * @return 店铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y) {
        return shopService.queryShopByTypeId(typeId, current, x, y);
    }

    /**
     * 根据店铺名称关键字分页查询店铺信息
     * @param name 店铺名称关键字
     * @param current 页码
     * @return 店铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}

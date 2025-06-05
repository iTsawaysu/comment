package com.sun.comment.controller;

import com.sun.comment.common.Result;
import com.sun.comment.entity.ShopType;
import com.sun.comment.service.ShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author sun
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private ShopTypeService shopTypeService;

    /**
     * 展示商铺类型（缓存）
     */
    @GetMapping("/list")
    public Result<List<ShopType>> getShopTypeList() {
        return shopTypeService.getShopTypeList();
    }
}

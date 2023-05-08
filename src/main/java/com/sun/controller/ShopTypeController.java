package com.sun.controller;

import com.sun.common.CommonResult;
import com.sun.entity.ShopType;
import com.sun.service.ShopTypeService;
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
    public CommonResult<List<ShopType>> getShopTypeList() {
        return shopTypeService.getShopTypeList();
    }
    
}

package com.sun.controller;

import com.sun.dto.Result;
import com.sun.service.ShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author sun
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private ShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        // List<ShopType> typeList = typeService.query().orderByAsc("sort").list();
        // return typeService.usingStringToQueryByCacheOrderByAscSort();
        return typeService.usingListToQueryByCacheOrderByAscSort();
    }
}

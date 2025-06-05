package com.sun.comment.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.sun.comment.common.Result;
import com.sun.comment.common.ReturnCode;
import com.sun.comment.common.SystemConstants;
import com.sun.comment.common.exception.BusinessException;
import com.sun.comment.common.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

/**
 * @author sun
 */
@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {
    /**
     * 上传文件
     */
    @PostMapping("/blog")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String suffix = StrUtil.subAfter(originalFilename, ".", true);
            String fileName = UUID.randomUUID().toString(true) + StrUtil.DOT + suffix;
            // 保存文件
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR + fileName));
            log.debug("文件上传成功，{}", fileName);
            return Result.success(fileName);
        } catch (IOException e) {
            throw new BusinessException(ReturnCode.OPERATION_ERROR, "文件上传失败");
        }
    }

    /**
     * 删除文件
     */
    @GetMapping("/blog/delete")
    public Result<String> deleteBlogImg(@RequestParam("name") String filename) {
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
        ThrowUtils.throwIf(file.isDirectory(), ReturnCode.OPERATION_ERROR, "错误的文件名称");
        FileUtil.del(file);
        return Result.success("删除成功");
    }
}

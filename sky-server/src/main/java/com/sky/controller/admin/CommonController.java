package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

//通用接口

@RestController
@RequestMapping("/admin/common")
@Slf4j
public class CommonController {

    //允许上传的图片后缀：项目里上传的口子是菜品/套餐图片，不需要放开任意类型
    private static final List<String> ALLOWED_EXTENSIONS =
            Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp");

    @Autowired
    private AliOssUtil aliOssUtil;

    //文件上传
    @PostMapping("/upload")
    public Result<String> upload(MultipartFile file){
        String originalFilename = file.getOriginalFilename();
        log.info("文件上传：{}", originalFilename);

        //前端 beforeUpload 也会拦一次，但那可以被绕过，真正的白名单校验放在这里
        int dotIndex = originalFilename == null ? -1 : originalFilename.lastIndexOf(".");
        if (dotIndex < 0
                || !ALLOWED_EXTENSIONS.contains(originalFilename.substring(dotIndex + 1).toLowerCase())) {
            log.warn("拒绝上传非图片文件：{}", originalFilename);
            return Result.error(MessageConstant.UPLOAD_TYPE_NOT_ALLOWED);
        }

        try {
            //截取原始文件名的后缀
            String extension = originalFilename.substring(dotIndex);
            //构造新文件名称
            String objectName = UUID.randomUUID() + extension;

            //文件的请求路径
            String filePath = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(filePath);
        } catch (Exception e) {
            //上传失败（OSS异常、IO异常等）统一返回失败，不能上报成功
            log.error("文件上传失败:", e);
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }
    }
}

package com.zymcloud.minioutil.controller;

import com.zymcloud.minioutil.config.FileSuffixEnum;
import com.zymcloud.minioutil.template.MinioTemplate;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * @author zhaoyimeng
 * @date 2021/05/25
 */
@RestController
@RequestMapping("/minio")
public class MinioController {
    @Resource
    private MinioTemplate minioTemplate;
    @Value("${minio.bucketName}")
    private String bucketName;

    /**
     * 创建bucket
     * @param name bucket名称
     * @return
     * @throws Exception
     */
    @PostMapping("/createBucket")
    public boolean createBucket(String name) throws Exception{
        return minioTemplate.createBucket(name);
    }

    /**
     * 上传文件
     * @param file 文件
     * @return 文件对象名（实际存储名称，非文件原始名称）
     */
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws Exception {
        return minioTemplate.putObject(bucketName, file);
    }

    /**
     * 预览
     * @param objectName 对象名称
     * @param width 图片缩略图宽度 为空表示查看原图
     * @param height 图片缩略图高度 为空表示查看原图
     * @param response   响应体
     * @param request    请求体
     */
    @GetMapping("/preview")
    public void preview(@RequestParam("objectName") String objectName,
                        @RequestParam(value = "width",required = false) Integer width,
                        @RequestParam(value = "height",required = false)Integer height,
                        HttpServletResponse response, HttpServletRequest request) throws Exception {

        try (InputStream inputStream = minioTemplate.getObject(bucketName, objectName);) {
            String userAgent = request.getHeader("User-Agent");
            //解决乱码
            if ( //IE 8 至 IE 10
                    userAgent.toUpperCase().contains("MSIE") ||
                            //IE 11
                            userAgent.contains("Trident/7.0")) {
                objectName = java.net.URLEncoder.encode(objectName, "UTF-8");
            } else {
                objectName = new String(objectName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
            }
            // 获取到文件的真实名称 用于下载显示
            String[] split = objectName.split("####");
            String suffix = objectName.substring(objectName.lastIndexOf(".") + 1);
            response.setHeader("Content-Disposition", "inline;filename=" + split[0] + "." + suffix);
            if (FileSuffixEnum.PDF.getFileSuffix().equalsIgnoreCase(suffix)){
                response.setContentType("application/pdf");
            }else {
                response.setContentType("text/plain");
            }
            response.setCharacterEncoding("UTF-8");

            // 如果文件是图片，并且传了图片宽度高度，则以缩略图进行展示
            List<String> imageSuffix = Arrays.asList("bmp", "dib", "gif", "jfif", "jpe", "jpeg", "jpg", "png", "tif", "tiff", "ico");
            if (imageSuffix.contains(suffix.toLowerCase()) && width!=null && height!=null){
                // .crop(Positions.CENTER) 按照size剪裁图片，多余部分不要
                // .addFilter(new Canvas(width,height,Positions.CENTER)) 按照size剪裁图片，多余部分用黑色填充
                Thumbnails.of(inputStream).size(width,height).crop(Positions.CENTER).outputQuality(0.8f).toOutputStream(response.getOutputStream());
            }else {
                FileCopyUtils.copy(inputStream, response.getOutputStream());
            }

        } catch (Exception e) {
            throw new Exception("文件读取异常");
        }
    }

    /**
     * 下载文件
     * @param objectName 文件对象名
     * @param response   响应体
     */
    @GetMapping("/download")
    public void download(@RequestParam("objectName") String objectName, HttpServletResponse response, HttpServletRequest request) throws Exception {
        try {
            InputStream is = minioTemplate.getObject(bucketName, objectName);
            String userAgent = request.getHeader("User-Agent");
            //解决乱码
            if ( //IE 8 至 IE 10
                    userAgent.toUpperCase().contains("MSIE") ||
                            //IE 11
                            userAgent.contains("Trident/7.0")) {
                objectName = java.net.URLEncoder.encode(objectName, "UTF-8");
            } else {
                objectName = new String(objectName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
            }
            // 获取到文件的真实名称 用于下载显示
            String[] split = objectName.split("####");
            String suffix = objectName.substring(objectName.lastIndexOf(".") + 1);
            response.setHeader("Content-Disposition", "attachment;filename=" + split[0] + "." + suffix);
            response.setContentType("application/octet-stream");
            response.setCharacterEncoding("UTF-8");
            IOUtils.copy(is, response.getOutputStream());
        } catch (IOException e) {
            throw new Exception("下载异常");
        }
    }

    /**
     * 移除文件
     * @param objectName 文件对象名
     */
    @PostMapping("/delete")
    public void remove(@RequestParam("objectName") String objectName) throws Exception {
        minioTemplate.removeObject(bucketName, objectName);
    }
}

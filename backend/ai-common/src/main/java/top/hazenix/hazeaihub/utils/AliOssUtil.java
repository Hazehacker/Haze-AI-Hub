package top.hazenix.hazeaihub.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.hazenix.hazeaihub.properties.AliOssProperties;


import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Slf4j
@Component
@RequiredArgsConstructor
public class AliOssUtil {


    private final AliOssProperties aliOssProperties;

    /**
     * 文件上传
     *
     * @param bytes
     * @param objectName
     * @return
     */
    public String upload(byte[] bytes, String objectName) {

        //截取原始文件名的后缀
        String extension = objectName.substring(objectName.lastIndexOf("."));
        objectName = UUID.randomUUID().toString() + extension;

        //【获取配置属性】(从注入的aliOssproperties中获取)
        String endpoint = aliOssProperties.getEndpoint();
        String accessKeyId = aliOssProperties.getAccessKeyId();
        String accessKeySecret = aliOssProperties.getAccessKeySecret();
        String bucketName = aliOssProperties.getBucketName();
        
        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            // 创建PutObject请求。
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(bytes));
        } catch (OSSException oe) {
            log.info("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            log.info("Error Message:{}" ,oe.getErrorMessage());
            log.info("Error Code:{}" ,oe.getErrorCode());
            log.info("Request ID:" + oe.getRequestId());
            log.info("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            log.info("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            log.info("Error Message:" + ce.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }

        // 拼接文件访问路径
        // 文件访问路径规则 https://BucketName.Endpoint/ObjectName
        StringBuilder stringBuilder = new StringBuilder("https://");
        stringBuilder
                .append(bucketName)
                .append(".")
                .append(endpoint)
                .append("/")
                .append(objectName);

        log.info("文件上传到:{}", stringBuilder.toString());

        return stringBuilder.toString();
    }


    /**
     * 文件上传并【返回临时签名URL】（有效期120分钟）
     *
     * @param bytes       文件字节数组
     * @param objectName  原始文件名（用于提取后缀）
     * @return 临时可访问的签名URL（例如：https://xxx?Expires=...&OSSAccessKeyId=...&Signature=...）
     */
    public String upload2(byte[] bytes, String objectName) {
        // 1. 生成唯一文件名
        String extension = objectName.substring(objectName.lastIndexOf("."));
        String uniqueObjectName = UUID.randomUUID().toString() + extension;

        // 2. 获取OSS配置
        String endpoint = aliOssProperties.getEndpoint();
        String accessKeyId = aliOssProperties.getAccessKeyId();
        String accessKeySecret = aliOssProperties.getAccessKeySecret();
        String bucketName = aliOssProperties.getBucketName();

        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

            // 3. 上传文件到OSS
            ossClient.putObject(bucketName, uniqueObjectName, new ByteArrayInputStream(bytes));

            // 4. 生成临时签名URL（有效期120分钟）
            Date expiration = new Date(System.currentTimeMillis() + 120 * 60 * 1000); // 120分钟
            URL signedUrl = ossClient.generatePresignedUrl(bucketName, uniqueObjectName, expiration);

            log.info("文件上传成功，临时访问地址: {}", signedUrl.toString());
            return signedUrl.toString();

        } catch (OSSException oe) {
            log.error("OSS异常 - 错误码: {}, 消息: {}, RequestId: {}",
                    oe.getErrorCode(), oe.getErrorMessage(), oe.getRequestId());
            throw new RuntimeException("文件上传失败", oe);
        } catch (ClientException ce) {
            log.error("客户端异常 - 消息: {}", ce.getMessage());
            throw new RuntimeException("文件上传失败", ce);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    /**
     * 后端签发临时访问凭证给前端，用于前端直传OSS
     * @param userId
     * @return
     * @throws ClientException
     */
    public Map<String, String> getTempCredentials(Long userId) throws com.aliyuncs.exceptions.ClientException {
        String accessKeyId = aliOssProperties.getAccessKeyId();
        String accessKeySecret = aliOssProperties.getAccessKeySecret();
        String roleArn = aliOssProperties.getRoleArn();
        String region = aliOssProperties.getRegion();
        String roleSessionName = aliOssProperties.getRoleSessionName();
        // 1. 初始化客户端
        DefaultProfile profile = DefaultProfile.getProfile(region, accessKeyId, accessKeySecret);
        DefaultAcsClient client = new DefaultAcsClient(profile);

        // 2. 构造请求
        AssumeRoleRequest request = new AssumeRoleRequest();
        request.setMethod(MethodType.POST);
        request.setRoleArn(roleArn);
        request.setRoleSessionName(roleSessionName + "-" + userId);
        request.setDurationSeconds(900L); // 临时凭证有效期：15分钟 (此api最小允许15min，最大允许1h)

        // 3. 限制上传路径（只能上传到自己的目录）
        String policy = String.format("""
            {
              "Version": "1",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Action": ["oss:PutObject", "oss:PostObject"],
                  "Resource": "acs:oss:*:*:%s/music/%d/*"
                }
              ]
            }
            """,
                aliOssProperties.getBucketName(),
                userId
        );
        request.setPolicy(policy);

        // 4. 获取临时凭证
        AssumeRoleResponse response = client.getAcsResponse(request);

        Map<String, String> result = new HashMap<>();
        result.put("accessKeyId", response.getCredentials().getAccessKeyId());
        result.put("accessKeySecret", response.getCredentials().getAccessKeySecret());
        result.put("securityToken", response.getCredentials().getSecurityToken());
        result.put("bucket", aliOssProperties.getBucketName());
        result.put("region", region);
        result.put("endpoint", aliOssProperties.getEndpoint());
        result.put("dir", "music/" + userId + "/");
        return result;
    }
}
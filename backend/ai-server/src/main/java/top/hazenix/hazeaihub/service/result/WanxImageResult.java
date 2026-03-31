package top.hazenix.hazeaihub.service.result;

import lombok.Data;

@Data
public class WanxImageResult {
    private String imageUrl; // OSS URL
    private String prompt; // original prompt
    private String ossKey; // OSS storage key
    private String originalUrl; // original Wanx CDN URL
}
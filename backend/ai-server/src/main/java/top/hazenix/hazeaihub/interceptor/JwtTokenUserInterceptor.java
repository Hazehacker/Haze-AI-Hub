package top.hazenix.hazeaihub.interceptor;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import top.hazenix.hazeaihub.constant.JwtClaimsConstant;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.properties.JwtProperties;
import top.hazenix.hazeaihub.utils.JwtUtil;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * jwt令牌校验的拦截器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenUserInterceptor implements HandlerInterceptor {


    private final JwtProperties jwtProperties;

//    private final RedisTemplate redisTemplate;

    /**
     * 校验jwt
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("JWT User Interceptor 执行: URI = {}", request.getRequestURI());

        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }
        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());
//        //在JWT验证时增加黑名单检查
//        if(isTokenInBlacklist( token)){
//            return false;
//        }
        // 暂时舍弃掉黑名单检查

        //2、校验令牌
        try {
            log.info("jwt校验:{}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            log.info("1");
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            BaseContext.setCurrentId(userId);
            log.info("当前用户id：{}", userId);

            //3、通过，放行
            return true;
        } catch (Exception ex) {
            //4、不通过，响应401状态码
            response.setStatus(401);
            return false;
        }
    }
    // 在JWT验证时增加黑名单检查
//    private boolean isTokenInBlacklist(String token) {
//        String key = "jwt:blacklist:" + getTokenSignature(token);
//        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
//    }
    private String getTokenSignature(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        String[] chunks = token.split("\\.");
        if (chunks.length > 2) {
            return chunks[2]; // signature part
        }
        return null;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        BaseContext.removeCurrentId();
    }





}

package top.hazenix.hazeaihub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import top.hazenix.hazeaihub.interceptor.JwtTokenAdminInterceptor;
import top.hazenix.hazeaihub.interceptor.JwtTokenUserInterceptor;
import top.hazenix.hazeaihub.json.JacksonObjectMapper;


import java.util.List;

/**
 * 配置类，注册web层相关组件
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {


    private final JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    private final JwtTokenUserInterceptor jwtTokenUserInterceptor;

    /**
     * 注册自定义拦截器
     *
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns(
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/swagger-resources/**"
                );
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/swagger-resources/**"
                );
    }

    /**
     * 通过knife4j生成接口文档的相关配置 (OpenAPI 3.0)
     * @return
     */
    @Bean
    public OpenAPI customOpenAPI() {
        log.info("准备生成接口文档");
        return new OpenAPI()
                .info(new Info()
                        .title("AI-Hub项目接口文档")
                        .version("2.0")
                        .description("AI-Hub项目接口测试文档"));
    }










    /**
     * (knife4j)给doc.html设置静态资源映射
     * @param registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("开始设置静态资源映射");
        
        // Knife4j 文档页面和静态资源
        registry.addResourceHandler("/doc.html**", "/*.js", "/*.css", "/*.png", "/*.ico")
                .addResourceLocations("classpath:/META-INF/resources/");
        
        // webjars 资源
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
        
        // Swagger UI 资源
        registry.addResourceHandler("/swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/swagger-ui/");
    }

    /**
     * 调整返回的时间格式
     * @param converters
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters){
        //创建一个消息转换器对象
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();

        //需要为消息转换器设置一个对象转换器，对象转换器可以将java对象序列化为json数据
        converter.setObjectMapper(new JacksonObjectMapper());
        //把自己的消息转换器加到converter容器,并把自己的消息转换器优先级放到最高
        converters.add(0,converter);
    }


}

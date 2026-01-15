package top.hazenix.hazeaihub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;


import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
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
public class WebMvcConfiguration extends WebMvcConfigurationSupport {


    private final JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    private final JwtTokenUserInterceptor jwtTokenUserInterceptor;

    /**
     * 注册自定义拦截器
     *
     * @param registry
     */
    protected void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**");
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/api/v1/**");


    }

    /**
     * 通过knife4j生成接口文档的相关配置
     * @return
     */
//    @Bean
//    public Docket docket() {
//        ApiInfo apiInfo = new ApiInfoBuilder()
//                .title("个人博客项目接口测试")
//                .version("2.0")
//                .description("个人博客项目接口测试")
//                .build();
//        Docket docket = new Docket(DocumentationType.SWAGGER_2)
//                .apiInfo(apiInfo)
//                .select()
//                //扫描的包要写对
//                .apis(RequestHandlerSelectors.basePackage("top.hazenix.controller"))
//                .paths(PathSelectors.any())
//                .build();
//        return docket;
//    }
    @Bean
    public Docket docketAdmin() {
        log.info("准备生成接口文档");
        ApiInfo apiInfo = new ApiInfoBuilder()
                .title("AI-Hub项目接口测试")
                .version("1.0")
                .description("AI-Hub项目接口测试")
                .build();
        Docket docket = new Docket(DocumentationType.SWAGGER_2)
                .groupName("管理端接口")
                .apiInfo(apiInfo)
                .select()
                //扫描的包要写对
                .apis(RequestHandlerSelectors.basePackage("top.hazenix.hazeaihub.controller.admin"))
                .paths(PathSelectors.any())
                .build();
        return docket;
    }
    @Bean
    public Docket docketUser() {
        log.info("准备生成接口文档");
        ApiInfo apiInfo = new ApiInfoBuilder()
                .title("AI-Hub项目接口测试")
                .version("2.0")
                .description("AI-Hub项目接口测试")
                .build();
        Docket docket = new Docket(DocumentationType.SWAGGER_2)
                .groupName("用户端接口")
                .apiInfo(apiInfo)
                .select()
                //扫描的包要写对
                .apis(RequestHandlerSelectors.basePackage("top.hazenix.hazeaihub.controller.user"))
                .paths(PathSelectors.any())
                .build();
        return docket;
    }










    /**
     * (knife4j)给doc.html设置静态
     * 资源映射
     * @param registry
     */
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        //如果不设置静态资源映射,访问http://localhost:8080/doc.html，idea会把doc.html看成一个controll层的一个接口
        log.info("开始设置静态资源映射");
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    /**
     * 调整返回的时间格式
     * @param converters
     */
    @Override
    protected void extendMessageConverters(List<HttpMessageConverter<?>> converters){
        //创建一个消息转换器对象
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();

        //需要为消息转换器设置一个对象转换器，对象转换器可以将java对象序列化为json数据
        converter.setObjectMapper(new JacksonObjectMapper());
        //把自己的消息转换器加到converter容器,并把自己的消息转换器优先级放到最高
        converters.add(0,converter);

        super.extendMessageConverters(converters);

    }


}

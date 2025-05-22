package com.sun.comment.config;

import com.sun.comment.interceptor.LoginInterceptor;
import com.sun.comment.interceptor.RefreshTokenInterceptor;
import javax.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    @Resource
    private RefreshTokenInterceptor refreshTokenInterceptor;
    @Resource
    private LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(refreshTokenInterceptor).addPathPatterns("/**").order(0);

        registry.addInterceptor(loginInterceptor).excludePathPatterns(
                "/shop/**", "/shop-type/**", "/upload/**", "/voucher/**",
                "/blog/hot", "/user/login", "/user/code"
        ).order(1);
    }
}

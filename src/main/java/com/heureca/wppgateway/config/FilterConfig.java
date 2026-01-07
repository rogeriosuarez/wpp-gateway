package com.heureca.wppgateway.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.heureca.wppgateway.service.ApiAuthenticationFilter;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<ApiAuthenticationFilter> apiFilter(ApiAuthenticationFilter filter) {
        FilterRegistrationBean<ApiAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }
}

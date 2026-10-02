package com.exemplo.pedidos.adapters.in.web.security;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SyntheticTokenProperties.class)
class SecurityConfig implements WebMvcConfigurer {

    @Bean
    FilterRegistrationBean<SyntheticTokenFilter> syntheticTokenFilter(SyntheticTokenProperties properties) {
        FilterRegistrationBean<SyntheticTokenFilter> registration =
                new FilterRegistrationBean<>(new SyntheticTokenFilter(properties));
        registration.setOrder(0);
        return registration;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CallerArgumentResolver());
    }
}

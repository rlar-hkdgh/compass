package com.example.compass.student.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.password.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.*;
import org.springframework.security.core.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.session.*;
import org.springframework.security.web.csrf.*;
import java.util.*;
@Configuration public class SecurityConfig {
    @Bean PasswordEncoder encoder(){
        return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
    @Bean SessionRegistry sessionRegistry(){
        return new SessionRegistryImpl();
    }
    @Bean HttpSessionEventPublisher sessionEvents(){
        return new HttpSessionEventPublisher();
    }
    @Bean SecurityContextRepository contexts(){
        return new HttpSessionSecurityContextRepository();
    }
    @Bean HttpSessionCsrfTokenRepository csrfRepository(){
        return new HttpSessionCsrfTokenRepository();
    }
    @Bean SessionAuthenticationStrategy loginSessions(SessionRegistry registry,HttpSessionCsrfTokenRepository csrf){
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),new CsrfAuthenticationStrategy(csrf),new RegisterSessionAuthenticationStrategy(registry)));
    }
    @Bean SecurityFilterChain chain(HttpSecurity http,SecurityContextRepository contexts,SessionRegistry registry,HttpSessionCsrfTokenRepository csrf,@Value("${compass.security.allowed-origins:}") String origins,@Value("${compass.docs.enabled:false}") boolean docsEnabled,@Value("${compass.demo.enabled:false}") boolean demoEnabled) throws Exception {
        var allowed=Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
        for(String origin:allowed){
            var uri=java.net.URI.create(origin);
            if(origin.contains("*")||uri.getHost()==null||(!"http".equals(uri.getScheme())&&!"https".equals(uri.getScheme()))||uri.getRawQuery()!=null||uri.getRawFragment()!=null||uri.getUserInfo()!=null||(uri.getPath()!=null&&!uri.getPath().isEmpty()))throw new IllegalArgumentException("Exact frontend origins are required");
        }
        var cors=new CorsConfiguration();
        cors.setAllowedOrigins(allowed);
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN"));
        var source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",cors);
        http.cors(c->c.configurationSource(source))    .csrf(c->c.csrfTokenRepository(csrf))    .securityContext(c->c.securityContextRepository(contexts))    .requestCache(c->c.disable())    .authorizeHttpRequests(c->{ if(demoEnabled)c.requestMatchers(org.springframework.http.HttpMethod.GET,"/demo","/demo/**").permitAll(); if(docsEnabled)c.requestMatchers("/swagger-ui.html","/swagger-ui/**","/v3/api-docs","/v3/api-docs/**","/v3/api-docs.yaml").permitAll(); c.requestMatchers("/api/v1/auth/csrf","/api/v1/auth/signup","/api/v1/auth/login","/api/v1/auth/signup/email/availability","/api/v1/auth/signup/email/send","/api/v1/auth/signup/email/confirm").permitAll()      .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/recruit-codes/**","/api/v1/student-codes/**").permitAll()      .requestMatchers("/api/v1/me","/api/v1/me/**").authenticated().anyRequest().denyAll();})    .exceptionHandling(c->c.authenticationEntryPoint((q,r,e)->error(r,401,"UNAUTHENTICATED"))      .accessDeniedHandler((q,r,e)->error(r,403,e instanceof CsrfException?"CSRF_INVALID":"FORBIDDEN")))    .addFilterBefore(new ConcurrentSessionFilter(registry,event->error(event.getResponse(),401,"SESSION_EXPIRED")),org.springframework.security.web.access.intercept.AuthorizationFilter.class)    .logout(c->c.logoutUrl("/api/v1/auth/logout").addLogoutHandler((q,r,a)->{
            var session=q.getSession(false);if(session!=null)registry.removeSessionInformation(session.getId());
        }).deleteCookies("JSESSIONID").logoutSuccessHandler((q,r,a)->r.setStatus(204)));
        return http.build();
    }
    static void error(jakarta.servlet.http.HttpServletResponse r,int status,String code) throws java.io.IOException {
        r.setStatus(status);
        r.setCharacterEncoding("UTF-8");
        r.setContentType("application/json");
        r.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+code+"\"}");
    }
}

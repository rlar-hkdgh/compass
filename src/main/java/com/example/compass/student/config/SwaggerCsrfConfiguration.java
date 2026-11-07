package com.example.compass.student.config;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springdoc.core.properties.*;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.*;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
@Configuration
@ConditionalOnProperty(name="compass.docs.enabled",havingValue="true")
public class SwaggerCsrfConfiguration {
 @Bean SwaggerIndexTransformer compassSwaggerTransformer(SwaggerUiConfigProperties config,SwaggerUiOAuthProperties oauth,SwaggerWelcomeCommon welcome,ObjectMapperProvider mapper) {
  return new SwaggerIndexPageTransformer(config,oauth,welcome,mapper) {
   @Override public Resource transform(HttpServletRequest request,Resource resource,ResourceTransformerChain chain) throws IOException {
    Resource result=super.transform(request,resource,chain);
    if(!"swagger-initializer.js".equals(resource.getFilename())) return result;
    String script;
    try(var input=result.getInputStream()){script=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
    String interceptor="""
async function(request) {
const target = new URL(request.url, window.location.href);
const base = window.location.pathname.split('/swagger-ui')[0];
if (target.origin !== window.location.origin || !target.pathname.startsWith(base + '/api/v1/')) return request;
request.credentials = 'same-origin';
if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method.toUpperCase())) {
const response = await fetch(base + '/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store' });
if (!response.ok) throw new Error('CSRF token request failed');
const csrf = await response.json();
request.headers[csrf.headerName] = csrf.token;
}
return request;
}
     """;
    script=script.replace("SwaggerUIBundle({","SwaggerUIBundle({requestInterceptor: "+interceptor+",");
    return new TransformedResource(result,script.getBytes(StandardCharsets.UTF_8));
   }
  };
 }
}
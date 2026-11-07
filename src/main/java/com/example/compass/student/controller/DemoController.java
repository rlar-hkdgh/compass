package com.example.compass.student.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

@Hidden
@RestController
@RequestMapping("/demo")
@ConditionalOnProperty(name="compass.demo.enabled",havingValue="true")
public class DemoController {
    @GetMapping public ResponseEntity<Void> redirect(HttpServletRequest request){
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(request.getContextPath()+"/demo/")).build();
    }
    @GetMapping(value={"/","/index.html"},produces="text/html;charset=UTF-8")
    public ResponseEntity<Resource> index(){return resource("index.html",MediaType.TEXT_HTML);}
    @GetMapping(value={"/app.js","/api-client.js"},produces="text/javascript;charset=UTF-8")
    public ResponseEntity<Resource> script(HttpServletRequest request){
        String uri=request.getRequestURI();
        return resource(uri.substring(uri.lastIndexOf('/')+1),MediaType.parseMediaType("text/javascript;charset=UTF-8"));
    }
    @GetMapping(value="/styles.css",produces="text/css;charset=UTF-8")
    public ResponseEntity<Resource> style(){return resource("styles.css",MediaType.parseMediaType("text/css;charset=UTF-8"));}
    private ResponseEntity<Resource> resource(String file,MediaType type){
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore())
            .header("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'")
            .body(new ClassPathResource("demo/"+file));
    }
}

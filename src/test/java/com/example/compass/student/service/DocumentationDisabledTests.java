package com.example.compass.student.service;

import com.example.compass.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
@SpringBootTest(properties={
    "spring.config.location=file:./src/main/resources/application.yml","spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:docs-disabled;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true",
    "compass.docs.enabled=false","compass.mail.enabled=false",
    "compass.recruit.crawl.enabled=false","compass.activity.collect.enabled=false"
})
@ContextConfiguration(initializers=TestDatabase.class)
class DocumentationDisabledTests {
    @Autowired WebApplicationContext context;
    @Test void readinessIsPublicWithDocumentationDisabled() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/healthz")).andExpect(status().isNoContent())
            .andExpect(header().doesNotExist("Set-Cookie"));
    }
    @Test void documentationAndDemoAreNotPublicByDefault() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for(String path:new String[]{"/swagger-ui.html","/swagger-ui/index.html","/v3/api-docs","/v3/api-docs.yaml","/demo","/demo/","/demo/app.js","/demo/api-client.js","/demo/styles.css"})
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
}

package com.example.compass.student.service;

import com.example.compass.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest(properties={
    "spring.config.location=file:./src/main/resources/application.yml","spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:demo-isolated;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true",
    "compass.demo.enabled=true","compass.docs.enabled=false","compass.mail.enabled=false",
    "compass.recruit.crawl.enabled=false","compass.activity.collect.enabled=false"
})
@ContextConfiguration(initializers=TestDatabase.class)
class DemoWebTests {
    @Autowired WebApplicationContext context;

    @Test void enabledDemoServesFormsAndAssetsWithoutExposingProtectedApis() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/demo")).andExpect(status().isFound()).andExpect(redirectedUrl("/demo/"));
        var html=mvc.perform(get("/demo/")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(header().string("Cache-Control","no-store"))
            .andReturn().getResponse();
        assertThat(html.getHeader("Content-Security-Policy")).contains("connect-src 'self'","frame-ancestors 'none'");
        var page=org.jsoup.Jsoup.parse(html.getContentAsString(StandardCharsets.UTF_8));
        assertThat(page.select("form[method=post]")).hasSize(9);
        assertThat(page.select("#profile-form #major-fields #major-rows, #profile-form #major-add[type=button]")).hasSize(2);
        assertThat(page.select("#profile-save").text()).isEqualTo("다음");
        assertThat(page.select("#transcript-file[type=file], #goal-job[required]")).hasSize(2);
        assertThat(page.select("#onboarding-finish, #courses-continue, #projects-continue, #internships-continue, #awards-continue, #certifications-continue")).hasSize(6);
        assertThat(page.select("#signup-transfer[type=checkbox], #profile-transfer[type=checkbox]")).hasSize(2);
        assertThat(page.select("#goal-form input[type=checkbox][name=companySizes]").eachAttr("value"))
            .containsExactly("LARGE","MID","STARTUP","SMALL");
        assertThat(page.select("#goal-form #career-type, #goal-form #target-role")).isEmpty();
        assertThat(page.select("#regions[role=group], #certification-codes[role=group], #target-date[type=month]")).hasSize(3);
        assertThat(page.select("#regions #region-groups, #regions #region-options, #region-selection, #region-clear")).hasSize(4);
        assertThat(page.select("#transcript-fields input[type=checkbox]")).isEmpty();
        assertThat(page.select("#profile-form #transcript-fields #transcript-preview[type=button], #profile-form #profile-save[type=submit]")).hasSize(2);
        assertThat(page.select("#profile-form #transcript-courses th").eachText()).containsExactly("원본 행", "수강 학기", "학수번호", "과목명", "등급");
        assertThat(page.select("#grade-year")).isEmpty();
        assertThat(page.select("#profile-form #semester")).isEmpty();
        assertThat(page.select("#profile-semester").text()).contains("이수학기");
        assertThat(page.select("#target-company").attr("placeholder")).contains("직접 입력");
        assertThat(page.select("script[src]").eachAttr("src")).containsExactly("app.js");
        mvc.perform(get("/demo/app.js")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/javascript"));
        mvc.perform(get("/demo/api-client.js")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/javascript"));
        mvc.perform(get("/demo/styles.css")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(get("/demo/.env")).andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/signup/email/availability")
            .contentType(MediaType.APPLICATION_JSON).content("{\"schoolEmail\":\"test@sju.ac.kr\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
}

package com.example.compass.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Loads ConfigData into a plain Environment: no application, web server, datasource or DB connection. */
class DotEnvConfigurationTests {
    @TempDir Path temporary;

    private StandardEnvironment load(boolean includeEnv,Map<String,Object> overrides) throws Exception {
        // Copy only configuration templates; never read the user's ignored .env or its password.
        String main=Files.readString(Path.of("src/main/resources/application.yml"));
        String imported=temporary.resolve(".env").toUri().toString();
        main=main.replace("file:./.env[.properties]",imported+"[.properties]");
        Files.writeString(temporary.resolve("application.yml"),main,StandardCharsets.UTF_8);
        Files.copy(Path.of("src/main/resources/application-supabase.yml"),temporary.resolve("application-supabase.yml"),StandardCopyOption.REPLACE_EXISTING);
        if(includeEnv)Files.copy(Path.of(".env.example"),temporary.resolve(".env"),StandardCopyOption.REPLACE_EXISTING);
        var environment=new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("explicit-test-settings",Map.of("spring.config.location",temporary.resolve("application.yml").toUri().toString())));
        if(!overrides.isEmpty())environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test-environment",overrides));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }

    @Test void templateActivatesSupabaseWithNoAutomaticDatabaseOrCollectionWrites() throws Exception {
        var env=load(true,Map.of());
        assertThat(env.getActiveProfiles()).containsExactly("supabase");
        assertThat(env.getRequiredProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require");
        assertThat(env.getRequiredProperty("spring.datasource.username")).isEqualTo("postgres.rqecthphhcvmnvxrbubg");
        assertThat(env.getProperty("spring.datasource.password","").isEmpty()).isTrue();
        assertThat(env.getProperty("spring.flyway.enabled",Boolean.class)).isFalse();
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        for(String flag:List.of("compass.recruit.import-enabled","compass.recruit.crawl.enabled","compass.activity.collect.enabled"))assertThat(env.getProperty(flag,Boolean.class)).isFalse();
        assertThat(env.getProperty("server.servlet.session.cookie.http-only",Boolean.class)).isTrue();
        assertThat(env.getProperty("server.servlet.session.cookie.secure",Boolean.class)).isFalse();
        assertThat(env.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(env.getProperty("server.port",Integer.class)).isEqualTo(8080);
        assertThat(env.getProperty("spring.main.web-application-type")).isEqualTo("servlet");
    }

    @Test void absentOptionalEnvPreservesExistingDefaultsAndDoesNotFailConfigLoading() throws Exception {
        var env=load(false,Map.of());
        assertThat(env.getActiveProfiles()).isEmpty();
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/compass");
        assertThat(env.getProperty("spring.flyway.enabled",Boolean.class)).isTrue();
        assertThat(env.getProperty("server.servlet.session.cookie.secure",Boolean.class)).isTrue();
    }

    @Test void runtimeEnvironmentOverridesFileWithoutRewritingIt() throws Exception {
        var env=load(true,Map.of("SUPABASE_DB_PASSWORD","configuration-test-marker","SESSION_COOKIE_SECURE","true"));
        // Only synthetic property markers are used; no real credentials are loaded or logged.
        assertThat(env.getProperty("spring.datasource.password").equals("configuration-test-marker")).isTrue();
        assertThat(env.getProperty("server.servlet.session.cookie.secure",Boolean.class)).isTrue();
        var properties=new Properties();
        try(var reader=Files.newBufferedReader(temporary.resolve(".env"),StandardCharsets.UTF_8)){properties.load(reader);}
        assertThat(properties.getProperty("SUPABASE_DB_PASSWORD").isEmpty()).isTrue();
    }
}

package com.example.compass;


import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.test.util.TestPropertyValues;

/** -PpostgresTest로 실행하면 H2 대신 임시 PostgreSQL에서 동일한 테스트를 수행한다. */
public class TestDatabase implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static EmbeddedPostgres postgres;
    @Override public void initialize(ConfigurableApplicationContext context) {
        if (!Boolean.getBoolean("compass.test.postgres")) return;
        try {
            synchronized (TestDatabase.class) {
                if(postgres==null) {
                    postgres=EmbeddedPostgres.builder().setPort(0).start();
                    Runtime.getRuntime().addShutdownHook(new Thread(()-> {
                        try { postgres.close(); } catch(Exception ignored) { }
                    }));
                }
            }
            TestPropertyValues.of(
                "spring.datasource.url="+postgres.getJdbcUrl("postgres","postgres"),
                "spring.datasource.username=postgres", "spring.datasource.password=postgres")
                .applyTo(context);
        } catch(Exception e) { throw new IllegalStateException("임시 PostgreSQL 시작 실패",e); }
    }
}

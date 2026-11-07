package com.example.compass.activity.collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;

@Component
@ConditionalOnProperty(name="compass.activity.collect.enabled",havingValue="true")
public class ActivityCollectionRunner implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(ActivityCollectionRunner.class);
    static final Set<String> SUPPORTED_TYPES=Set.of("COURSE","EXTRACURRICULAR","INTERNSHIP","BOOTCAMP");
    private final Environment env;private final OfficialActivityClient client;private final CollectedActivityStore store;
    private final ConfigurableApplicationContext context;
    private final SejongJobCenterCollector jobCenter;
    private final SejongDodreamCollector dodream;
    private final BootcampCollector bootcamps;
    
    public ActivityCollectionRunner(Environment env,OfficialActivityClient client,CollectedActivityStore store,ConfigurableApplicationContext context,SejongJobCenterCollector jobCenter,SejongDodreamCollector dodream,BootcampCollector bootcamps) {
        this.env=env;this.client=client;this.store=store;this.context=context;this.jobCenter=jobCenter;this.dodream=dodream;this.bootcamps=bootcamps;
    }
    @Override public void run(ApplicationArguments args) throws Exception {
        try {
            if(!"none".equals(env.getProperty("spring.main.web-application-type"))) throw new IllegalArgumentException("Collection requires web-application-type=none");
            if(env.getProperty("compass.recruit.crawl.enabled",Boolean.class,false)) throw new IllegalArgumentException("Do not combine activity/recruit collectors");
            if(!"false".equals(env.getProperty("spring.flyway.enabled"))) throw new IllegalArgumentException("Collector requires Flyway disabled; validate migrations separately before collection");
            if(!"supabase".equals(String.join(",",env.getActiveProfiles()))) throw new IllegalArgumentException("Initial activity collection targets only the supabase profile");
            if(!"jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require".equals(env.getProperty("spring.datasource.url"))
                || !"postgres.rqecthphhcvmnvxrbubg".equals(env.getProperty("spring.datasource.username")))
                throw new IllegalArgumentException("Datasource differs from the approved sejong-compass target");
            boolean apply=env.getProperty("compass.activity.collect.apply",Boolean.class,false);
            String configured=env.getRequiredProperty("compass.activity.collect.plan");Path planFile=Path.of(configured);
            if(Files.size(planFile)>1_000_000) throw new IllegalArgumentException("Collection plan too large");
            var plan=JsonMapper.builder().build().readValue(Files.readString(planFile),ActivityCollectionPlan.class);
            validatePlan(plan);
            List<CollectedActivity> candidates=new ArrayList<>();
            String workbook=env.getProperty("compass.activity.collect.workbook","");
            if(!workbook.isBlank()) candidates.addAll(SejongCourseWorkbook.read(Path.of(workbook),2026,"SECOND_SEMESTER",
                env.getRequiredProperty("compass.activity.collect.course-source-url")));
            for(var target:plan.targets()) {
                var parsed=OfficialActivityParser.parse(target,client.fetch(target));
                if(parsed.isPresent()) candidates.add(parsed.get());
                else log.info("ACTIVITY_SKIPPED source={} reason=NOT_EVIDENCED_IT",target.source());
            }
            if (env.getProperty("compass.activity.collect.job-center.enabled",Boolean.class,false)) {
                candidates.addAll(jobCenter.collect());
            }
            if (env.getProperty("compass.activity.collect.dodream.enabled",Boolean.class,false)) {
                candidates.addAll(dodream.collect());
            }
            if(env.getProperty("compass.activity.collect.bootcamps.enabled",Boolean.class,false)) candidates.addAll(bootcamps.collect());
            validateCandidates(candidates);
            // Finish parsing/validation before first database write. No schedule or automatic retries.
            Path preview=Path.of(env.getRequiredProperty("compass.activity.collect.preview"));
            if(Files.exists(preview)) throw new IllegalArgumentException("Preview path already exists; preserve previous evidence");
            Files.writeString(preview,JsonMapper.builder().build().writeValueAsString(candidates),StandardOpenOption.CREATE_NEW);
            log.info("ACTIVITY_PREVIEW candidates={} counts={} apply={}",candidates.size(),counts(candidates),apply);
            if(apply) for(var candidate:candidates) {
                Long id=store.save(candidate);
                log.info("ACTIVITY_SAVED source={} id={} type={}",candidate.source(),id,candidate.type());
            }
            log.info("ACTIVITY_RESULT status={} count={}",apply ? "APPLIED" : "PREVIEW_ONLY",candidates.size());
        } finally { context.close(); }
    }
    static void validatePlan(ActivityCollectionPlan plan) {
        if(plan==null || plan.targets()==null) throw new IllegalArgumentException("Collection plan targets are required");
        for(var target:plan.targets()) {
            OfficialActivityParser.validateTarget(target);
            if(target.type().equals("COURSE")) throw new IllegalArgumentException("Courses must use the official timetable workbook adapter");
        }
    }
    static Map<String,Long> counts(List<CollectedActivity> list) {
        return list.stream().collect(java.util.stream.Collectors.groupingBy(CollectedActivity::type,TreeMap::new,java.util.stream.Collectors.counting()));
    }
    static void validateCandidates(List<CollectedActivity> list) {
        Set<String> ids=new HashSet<>();
        for(var value:list) if(!ids.add(value.source()+"\n"+value.externalId())) throw new IllegalArgumentException("Duplicate source identity in collection plan");
        counts(list).forEach((type,count)->{ if(!SUPPORTED_TYPES.contains(type)) throw new IllegalArgumentException("Unsupported candidate type: "+type); });
    }
}

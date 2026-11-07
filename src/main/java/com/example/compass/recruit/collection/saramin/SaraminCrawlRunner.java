package com.example.compass.recruit.collection.saramin;
import com.example.compass.recruit.exception.RecruitException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
@ConditionalOnProperty(name="compass.recruit.crawl.enabled",havingValue="true")
public class SaraminCrawlRunner implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(SaraminCrawlRunner.class);
    private final SaraminCrawler crawler;
    private final SaraminCollectedPostingStore store;
    private final Environment env;
    private final ConfigurableApplicationContext context;
    public SaraminCrawlRunner(SaraminCrawler crawler,SaraminCollectedPostingStore store,Environment env,ConfigurableApplicationContext context) {
        this.crawler=crawler;this.store=store;this.env=env;this.context=context;
    }
    private static class Counts {
        int pages,discovered,attempted,saved,skipped,duplicates;
        String status="ABORTED";
    }
    @Override public void run(ApplicationArguments args) throws Exception {
        Counts count=new Counts();
        try {
            if(!"none".equals(env.getProperty("spring.main.web-application-type")))
                throw new IllegalArgumentException("수집 실행에는 --spring.main.web-application-type=none을 지정하세요.");
            boolean discoverOnly=env.getProperty("compass.recruit.crawl.discover-only",Boolean.class,false);
            String configured=env.getProperty("compass.recruit.crawl.ids","");
            if(!configured.isBlank()) {
                var ids=Arrays.stream(configured.split(",",-1)).map(String::trim).distinct().toList();
                if(ids.stream().anyMatch(id->!id.matches("[0-9]{1,20}"))) throw new IllegalArgumentException("숫자 공고번호만 허용합니다.");
                count.discovered=ids.size();
                if(!discoverOnly) for(String id:ids) collect(id,count);
                count.status=discoverOnly ? "DISCOVERY_ONLY" : "COMPLETE";
                return;
            }
            int page=env.getProperty("compass.recruit.crawl.start-page",Integer.class,1);
            int maxPages=env.getProperty("compass.recruit.crawl.max-pages",Integer.class,0);
            if(page<1 || maxPages<0) throw new IllegalArgumentException("start-page는 1 이상, max-pages는 0 이상입니다.");
            Set<String> seen=new HashSet<>();
            while(true) {
                if(Thread.currentThread().isInterrupted()) throw new InterruptedException("수집이 중단됐습니다.");
                log.info("CRAWL_PAGE_START page={}",page);
                var listing=crawler.discoverPage(page);
                var ids=new ArrayList<String>();
                for(String id:listing.postingIds()) {
                    if(seen.add(id)) ids.add(id); else count.duplicates++;
                }
                if(!listing.postingIds().isEmpty() && ids.isEmpty())
                    throw new IOException("새 공고 없는 목록이 반복됩니다. 페이지="+page);
                count.discovered+=ids.size();
                if(!discoverOnly) for(String id:ids) collect(id,count);
                count.pages++;
                log.info("CRAWL_PAGE_DONE page={} found={} new={} savedTotal={} skippedTotal={} hasNext={}",
                    page,listing.postingIds().size(),ids.size(),count.saved,count.skipped,listing.hasNext());
                if(!listing.hasNext()) { count.status=discoverOnly ? "DISCOVERY_ONLY" : "COMPLETE";break; }
                if(maxPages>0 && count.pages>=maxPages) { count.status="PAGE_LIMIT";break; }
                page=Math.incrementExact(page);
            }
        } finally {
            log.info("CRAWL_RESULT status={} pages={} discovered={} attempted={} saved={} skipped={} duplicates={}",
                count.status,count.pages,count.discovered,count.attempted,count.saved,count.skipped,count.duplicates);
            context.close();
        }
    }
    private void collect(String id,Counts count) throws IOException,InterruptedException {
        count.attempted++;
        try {
            var data=crawler.collect(id);
            // A separate transactional service commits each posting independently.
            store.save(data);
            log.info("CRAWL_SAVED id={} company={} title={} chars={}",id,data.companyName(),data.title(),data.description().length());
            count.saved++;
        } catch(IllegalArgumentException e) {
            count.skipped++;
            log.info("CRAWL_SKIPPED id={} reason={}",id,e.getMessage());
        } catch(RecruitException e) {
            if(e.getStatus()!=400) throw e;
            count.skipped++;
            log.warn("CRAWL_SKIPPED id={} code={} reason={}",id,e.getCode(),e.getMessage());
        } catch(SaraminCrawler.HttpFailure e) {
            if(e.status()!=404 && e.status()!=410) throw e;
            count.skipped++;
            log.info("CRAWL_SKIPPED id={} reason=HTTP_{}",id,e.status());
        }
    }
}

package com.example.compass.recruit.collection.saramin;
import com.example.compass.recruit.entity.CodeValue;
import com.example.compass.recruit.entity.JobCategory;
import com.example.compass.recruit.entity.Specialty;
import com.example.compass.recruit.entity.TechStack;

import tools.jackson.databind.json.JsonMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Original site classification, not numeric ranges or guesses based on names. */
@Service
@Transactional(readOnly=true)
public class SaraminKeywordClassifier {
    private final EntityManager em;
    public SaraminKeywordClassifier(EntityManager em) { this.em=em; }
    public record Entry(String code,String name,String majorCode,String groupCode,String groupName) {}
    public record Catalog(String sourceUrl,String snapshotDate,List<Entry> entries) {}
    public record Classification(List<String> roles,List<String> specialties,List<String> technologies,List<String> unknown) {}
    private static final Catalog CATALOG=load();
    private static final Map<String,Entry> ENTRIES=index();

    public Classification classify(List<String> sourceCodes) {
        var roles=new ArrayList<String>();var specialties=new ArrayList<String>();
        var technologies=new ArrayList<String>();var unknown=new ArrayList<String>();
        var codes=new LinkedHashSet<>(sourceCodes==null ? List.<String>of() : sourceCodes);
        Map<String,String> database=new HashMap<>();
        if(!codes.isEmpty()) {
            addCodes(database,codes,JobCategory.class,"JOB");
            addCodes(database,codes,Specialty.class,"SPECIALTY");
            addCodes(database,codes,TechStack.class,"TECH_STACK");
        }
        for(String code:codes) {
            Entry entry=ENTRIES.get(code);
            String type=database.get(code);
            // Outside IT, use the source catalog only to detect additional real roles.
            if(type==null && entry!=null && !entry.majorCode().equals("2")) {
                type=switch(entry.groupName()) {
                    case "직무·직업" -> "JOB";
                    case "전문분야" -> "SPECIALTY";
                    case "기술스택" -> "TECH_STACK";
                    default -> "UNKNOWN";
                };
            }
            if(type==null) type="UNKNOWN";
            switch(type) {
                case "JOB" -> roles.add(code);
                case "SPECIALTY" -> specialties.add(code);
                case "TECH_STACK" -> technologies.add(code);
                default -> unknown.add(code);
            }
        }
        return new Classification(List.copyOf(roles),List.copyOf(specialties),List.copyOf(technologies),List.copyOf(unknown));
    }
    public List<String> itRoles(List<String> sourceCodes) {
        var result=classify(sourceCodes);
        var jobs=result.roles().stream().filter(code->em.find(JobCategory.class,code)!=null).toList();
        if(jobs.isEmpty()) throw new IllegalArgumentException("확인된 IT 직무가 없어 건너뜁니다. 미확인 코드 수="+result.unknown().size());
        return jobs;
    }
    public List<String> eligibleItRoles(Map<String,List<String>> codes) {
        if(!codes.getOrDefault("cat_mcls",List.of()).contains("2"))
            throw new IllegalArgumentException("IT개발·데이터 대분류(2)가 없어 건너뜁니다.");
        var classification=classify(codes.get("cat_kewd"));
        var jobs=classification.roles().stream().filter(code->em.find(JobCategory.class,code)!=null).toList();
        boolean hasItTechnology=classification.technologies().stream().anyMatch(code->em.find(TechStack.class,code)!=null);
        boolean hasItSpecialty=classification.specialties().stream().anyMatch(code->em.find(Specialty.class,code)!=null);
        if(jobs.isEmpty() && !hasItTechnology && !hasItSpecialty)
            throw new IllegalArgumentException("확인된 IT 직무·기술스택·전문분야가 없어 건너뜁니다.");
        return jobs;
    }
    private void addCodes(Map<String,String> types,Set<String> codes,Class<? extends CodeValue> entity,String type) {
        for(var item:em.createQuery("select c from "+entity.getSimpleName()+" c where c.code in :codes",entity)
                .setParameter("codes",codes).getResultList()) {
            if(types.putIfAbsent(item.getCode(),type)!=null)
                throw new IllegalStateException("여러 기준 테이블에 중복된 사람인 코드: "+item.getCode());
        }
    }
    public static String sourceUrl() { return CATALOG.sourceUrl(); }
    public static String snapshotDate() { return CATALOG.snapshotDate(); }
    private static Catalog load() {
        try(var input=SaraminKeywordClassifier.class.getResourceAsStream("/data/saramin-keyword-categories.json")) {
            if(input==null) throw new IllegalStateException("사람인 분류 스냅샷이 없습니다.");
            return JsonMapper.builder().build().readValue(input,Catalog.class);
        } catch(Exception e) { throw new IllegalStateException("사람인 코드 분류를 읽을 수 없습니다.",e); }
    }
    private static Map<String,Entry> index() {
        Map<String,Entry> values=new HashMap<>();
        for(var entry:CATALOG.entries()) {
            if(values.put(entry.code(),entry)!=null) throw new IllegalStateException("중복 사람인 분류 코드: "+entry.code());
        }
        return Map.copyOf(values);
    }
}



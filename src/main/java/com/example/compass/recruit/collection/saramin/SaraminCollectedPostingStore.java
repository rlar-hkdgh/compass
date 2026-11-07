package com.example.compass.recruit.collection.saramin;
import com.example.compass.recruit.entity.CodeValue;
import com.example.compass.recruit.entity.JobPosting;
import com.example.compass.recruit.entity.JobPostingCollectionMetadata;
import com.example.compass.recruit.entity.PostingId;
import com.example.compass.recruit.repository.PostingRepository;
import com.example.compass.recruit.service.RecruitService;
import com.example.compass.recruit.entity.Region;
import com.example.compass.recruit.entity.Specialty;
import com.example.compass.recruit.entity.TechStack;
import com.example.compass.recruit.dto.request.CompanyRequest;
import com.example.compass.recruit.dto.response.CodeResponse;
import com.example.compass.recruit.dto.request.CodeSelection;
import com.example.compass.recruit.dto.request.PostingRequest;
import com.example.compass.recruit.dto.response.PostingDetail;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
@Service
public class SaraminCollectedPostingStore {
    private final RecruitService service;
    private final PostingRepository postings;
    private final EntityManager em;
    private final Validator validator;
    private final SaraminKeywordClassifier classifier;
    public SaraminCollectedPostingStore(RecruitService service,PostingRepository postings,EntityManager em,Validator validator,SaraminKeywordClassifier classifier) {
        this.service=service;this.postings=postings;this.em=em;this.validator=validator;this.classifier=classifier;
    }
    @Transactional
    public PostingDetail save(SaraminPageParser.Collected data) {
        String experience=SaraminRecruitmentMapping.entryExperience(data.summary().get("경력"));
        List<String> jobs=classifier.eligibleItRoles(data.codes());
        PostingId id=new PostingId("SARAMIN",data.number());
        JobPosting existing=postings.findById(id).orElse(null);
        Map<String,Object> metadata=new LinkedHashMap<>();
        metadata.put("collectedAt",Instant.now().toString());
        metadata.put("bodyUrl",data.bodyUrl());metadata.put("summary",data.summary());
        metadata.put("originalCodes",data.codes());
        metadata.put("bodyOcr",data.bodyOcr());
        metadata.put("originalKeywordNames",data.keywordNames());
        metadata.put("classificationPolicy","Explicit IT major category 2 and at least one known IT role, IT technology or IT specialty are required; detailed job links may be empty; all known IT roles are linked; mixed IT/non-IT postings are allowed and original non-IT role codes are preserved in metadata; unknown tags are retained without inferred classification.");
        var classification=classifier.classify(data.codes().get("cat_kewd"));
        metadata.put("jobClassification",Map.of("roleCodes",classification.roles(),"itRoleCodes",jobs,"unknownCodes",classification.unknown(),
            "specialtyCodes",classification.specialties(),"technologyCodes",classification.technologies(),
            "sourceUrl",SaraminKeywordClassifier.sourceUrl(),"snapshotDate",SaraminKeywordClassifier.snapshotDate()));
        List<String> regions=data.codes().getOrDefault("loc_cd",List.of()).stream()
            .distinct().filter(value->em.find(Region.class,value)!=null).toList();
        Map<String,Object> deadlineMapping=new LinkedHashMap<>();
        deadlineMapping.put("code",data.deadlineTypeCode());deadlineMapping.put("evidence",data.deadlineEvidence());
        metadata.put("deadlineMapping",deadlineMapping);
        var education=SaraminConditionParser.education(data.summary().get("학력"));
        var employment=SaraminRecruitmentMapping.employmentTypes(data.summary().get("근무형태"),service.codes("employment-types"));
        metadata.put("employmentTypeMapping",employment);
        metadata.put("experiencePolicy","Only explicit entry-level, entry-level/experienced or experience-independent postings are collected.");
        var salary=SaraminConditionParser.salary(data.summary().get("급여"));
        metadata.put("educationMapping",education);metadata.put("salaryMapping",salary);
        // Reuse company only for this exact existing posting, not by company name.
        Long companyId=existing==null ? service.createCompany(new CompanyRequest(data.companyName(),data.companyUrl())).id() : existing.getCompany().getId();
        var request=new PostingRequest(companyId,data.title(),"https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx="+data.number(),
            data.active(),data.applicationStart(),data.applicationEnd(),
            java.util.stream.Stream.of(service.codes("job-categories"),service.codes("specialties"),service.codes("tech-stacks"))
                .flatMap(List::stream).filter(c->data.codes().getOrDefault("cat_kewd",List.of()).contains(c.code()))
                .map(CodeResponse::name).collect(java.util.stream.Collectors.joining(",")),data.description(),
            education.note(),salary.minAmount(),salary.maxAmount(),
            new CodeSelection(regions,employment.codes(),jobs,education.code(),data.deadlineTypeCode(),salary.unit(),salary.decision(),experience,
                knownCodes(classification.specialties(),Specialty.class),
                knownCodes(classification.technologies(),TechStack.class)));
        var violations=validator.validate(request);
        if(!violations.isEmpty()) throw new IllegalArgumentException("수집 필드가 저장 규칙에 맞지 않습니다: "+
            violations.stream().map(v->v.getPropertyPath()+" "+v.getMessage()).sorted()
                .collect(java.util.stream.Collectors.joining("; ")));
        var result=service.save("SARAMIN",data.number(),request);
        JobPostingCollectionMetadata saved=em.find(JobPostingCollectionMetadata.class,id);
        if(saved==null) { saved=new JobPostingCollectionMetadata(id); em.persist(saved); }
        var mapper=JsonMapper.builder().build();
        // Keep migration provenance when an existing posting is collected again.
        if(existing!=null && saved.getSourceMetadata()!=null) {
            var old=mapper.readTree(saved.getSourceMetadata());
            if(old.has("legacySalaryType")) metadata.put("legacySalaryType",old.get("legacySalaryType"));
        }
        saved.setSourceMetadata(mapper.writeValueAsString(metadata));
        return result;
    }
    private <T extends CodeValue> List<String> knownCodes(List<String> codes,Class<T> type) {
        return codes.stream().filter(code->em.find(type,code)!=null).distinct().toList();
    }
}




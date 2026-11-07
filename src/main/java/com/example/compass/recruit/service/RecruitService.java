package com.example.compass.recruit.service;
import com.example.compass.recruit.entity.CodeValue;
import com.example.compass.recruit.entity.Company;
import com.example.compass.recruit.entity.DeadlineType;
import com.example.compass.recruit.entity.EducationLevel;
import com.example.compass.recruit.entity.EmploymentType;
import com.example.compass.recruit.entity.ExperienceType;
import com.example.compass.recruit.entity.JobCategory;
import com.example.compass.recruit.entity.JobPosting;
import com.example.compass.recruit.entity.JobPostingDetail;
import com.example.compass.recruit.entity.PostingId;
import com.example.compass.recruit.repository.PostingRepository;
import com.example.compass.recruit.entity.PostingSource;
import com.example.compass.recruit.exception.RecruitException;
import com.example.compass.recruit.entity.Region;
import com.example.compass.recruit.entity.SalaryDecision;
import com.example.compass.recruit.entity.SalaryUnit;
import com.example.compass.recruit.entity.Specialty;
import com.example.compass.recruit.entity.TechStack;
import com.example.compass.recruit.dto.request.CompanyRequest;
import com.example.compass.recruit.dto.response.CompanyResponse;
import com.example.compass.recruit.dto.response.CodeResponse;
import com.example.compass.recruit.dto.request.PostingRequest;
import com.example.compass.recruit.dto.response.PostingSummary;
import com.example.compass.recruit.dto.response.PostingDetail;
import com.example.compass.recruit.dto.response.PageResponse;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly=true)
public class RecruitService {
    private final EntityManager em;
    private final PostingRepository postings;
    private static final Map<String,Class<? extends CodeValue>> CODE_TYPES = Map.ofEntries(
        Map.entry("sources", PostingSource.class), Map.entry("job-categories", JobCategory.class),
        Map.entry("regions", Region.class), Map.entry("employment-types", EmploymentType.class),
        Map.entry("specialties", Specialty.class), Map.entry("tech-stacks", TechStack.class), Map.entry("education-levels", EducationLevel.class),
        Map.entry("experience-types", ExperienceType.class), Map.entry("salary-units", SalaryUnit.class), Map.entry("salary-decisions", SalaryDecision.class),
        Map.entry("deadline-types", DeadlineType.class));

    public RecruitService(EntityManager em, PostingRepository postings) {
        this.em=em; this.postings=postings;
    }

    public List<CodeResponse> codes(String type) {
        var entity=CODE_TYPES.get(type);
        if (entity==null) throw new RecruitException(404,"CODE_TYPE_NOT_FOUND","지원하지 않는 코드 종류입니다.");
        return em.createQuery("select c from " + entity.getSimpleName() + " c order by c.code",entity)
            .getResultList().stream().map(RecruitService::code).toList();
    }

    @Transactional
    public CompanyResponse createCompany(CompanyRequest request) {
        validateUrl(request.companyInfoUrl());
        // 이름 또는 URL만으로 동일 기업이라고 자동 병합하지 않는다.
        Company company=new Company(request.name().trim(), request.companyInfoUrl());
        em.persist(company);
        return company(company);
    }

    @Transactional
    public PostingDetail save(String source,String sourcePostingId,PostingRequest r) {
        PostingId id=id(source,sourcePostingId);
        requiredCode(PostingSource.class,id.getSourceCode());
        validateUrl(r.sourceUrl());
        if (r.applicationStartAt()!=null && r.applicationEndAt()!=null
                && r.applicationStartAt().isAfter(r.applicationEndAt()))
            throw new RecruitException(400,"INVALID_APPLICATION_PERIOD","접수 시작일은 마감일보다 늦을 수 없습니다.");
        validateSalary(r);
        Company company=em.find(Company.class,r.companyId());
        if(company==null) throw new RecruitException(404,"COMPANY_NOT_FOUND","회사를 찾을 수 없습니다.");
        JobPosting p=postings.findById(id).orElseGet(()->new JobPosting(id));
        p.setCompany(company); p.setTitle(r.title().trim()); p.setActive(r.active());
        p.setApplicationStartAt(r.applicationStartAt()); p.setApplicationEndAt(r.applicationEndAt());

        var c=r.codes();
        var regions=c.regions()==null ? List.<Region>of() : c.regions().stream()
            .distinct().map(value->requiredCode(Region.class,value)).toList();
        p.getRegions().clear();
        p.getRegions().addAll(regions);
        var specialties=codeValues(Specialty.class,c.specialties());
        var techStacks=codeValues(TechStack.class,c.techStacks());
        p.getSpecialties().clear();p.getSpecialties().addAll(specialties);
        p.getTechStacks().clear();p.getTechStacks().addAll(techStacks);
        var employmentTypes=codeValues(EmploymentType.class,c.employmentTypes());
        p.getEmploymentTypes().clear();p.getEmploymentTypes().addAll(employmentTypes);
        var jobs=codeValues(JobCategory.class,c.jobCategories());
        p.getJobCategories().clear();p.getJobCategories().addAll(jobs);
        p.setEducationLevel(optionalCode(EducationLevel.class,c.educationLevel()));
        p.setDeadlineType(optionalCode(DeadlineType.class,c.deadlineType()));
        p.setSalaryUnit(optionalCode(SalaryUnit.class,c.salaryUnit()));
        p.setSalaryDecision(optionalCode(SalaryDecision.class,c.salaryDecision()));
        p.setSalaryMinAmount(r.salaryMinAmount());p.setSalaryMaxAmount(r.salaryMaxAmount());
        p.setEducationNote(r.educationNote());
        p.setExperienceType(optionalCode(ExperienceType.class,c.experienceType()));
        if (!em.contains(p)) em.persist(p);
        // Persist parent before the dependent detail row, in the same transaction.
        em.flush();
        JobPostingDetail d=em.find(JobPostingDetail.class,id);
        if(d==null) d=new JobPostingDetail(id);
        d.setSourceUrl(r.sourceUrl()); d.setKeywords(r.keywords()); d.setDescription(r.description());
        if(!em.contains(d)) em.persist(d);
        em.flush();
        return detail(p);
    }

    public PostingDetail get(String source,String sourcePostingId) {
        return detail(postings.findById(id(source,sourcePostingId))
            .orElseThrow(()->new RecruitException(404,"POSTING_NOT_FOUND","공고를 찾을 수 없습니다.")));
    }

    public PageResponse<PostingSummary> list(int page,int size,String source,String jobCode,String regionCode,Boolean active) {
        Specification<JobPosting> spec=(root,query,cb)->cb.conjunction();
        if(source!=null) {
            String normalized=source.toUpperCase(Locale.ROOT);
            requiredCode(PostingSource.class,normalized);
            spec=spec.and((root,q,cb)->cb.equal(root.get("id").get("sourceCode"),normalized));
        }
        if(jobCode!=null) {
            requiredCode(JobCategory.class,jobCode);
            spec=spec.and((root,q,cb)->cb.equal(root.join("jobCategories").get("code"),jobCode));
        }
        if(regionCode!=null) {
            requiredCode(Region.class,regionCode);
            spec=spec.and((root,q,cb)->cb.equal(root.join("regions").get("code"),regionCode));
        }
        if(active!=null) spec=spec.and((root,q,cb)->cb.equal(root.get("active"),active));
        var result=postings.findAll(spec,PageRequest.of(page,size,Sort.by(
            Sort.Order.asc("id.sourceCode"),Sort.Order.desc("id.sourcePostingId"))));
        return new PageResponse<>(result.getContent().stream().map(RecruitService::summary).toList(),
            page,size,result.getTotalElements(),result.getTotalPages());
    }

    private static void validateSalary(PostingRequest r) {
        Long min=r.salaryMinAmount(),max=r.salaryMaxAmount();
        boolean hasAmount=min!=null || max!=null;
        if((min!=null && min<0) || (max!=null && max<0) || (min!=null && max!=null && min>max))
            throw new RecruitException(400,"INVALID_SALARY_RANGE","급여 금액은 0 이상이며 최소 금액은 최대 금액보다 클 수 없습니다.");
        if(hasAmount && (r.codes().salaryUnit()==null || !"DISCLOSED".equals(r.codes().salaryDecision())))
            throw new RecruitException(400,"INVALID_SALARY","금액이 있으면 지급 단위와 금액 명시 결정 방식이 필요합니다.");
        if(!hasAmount && "DISCLOSED".equals(r.codes().salaryDecision()))
            throw new RecruitException(400,"INVALID_SALARY","금액 명시에는 최소 또는 최대 금액이 필요합니다.");
    }
    private <T extends CodeValue> List<T> codeValues(Class<T> type,List<String> values) {
        if(values==null) return List.of();
        return values.stream().distinct().map(value->requiredCode(type,value)).toList();
    }
    private <T extends CodeValue> T requiredCode(Class<T> type,String value) {
        T item=em.find(type,value);
        if(item==null) throw new RecruitException(400,"INVALID_CODE",type.getSimpleName()+"에 없는 코드입니다: "+value);
        return item;
    }
    private <T extends CodeValue> T optionalCode(Class<T> type,String value) {
        return value==null ? null : requiredCode(type,value);
    }
    private static PostingId id(String source,String number) {
        if(source==null || number==null || number.isBlank() || number.length()>64 || source.length()>32)
            throw new RecruitException(400,"INVALID_POSTING_KEY","공고 식별값이 올바르지 않습니다.");
        return new PostingId(source.toUpperCase(Locale.ROOT),number);
    }
    private static void validateUrl(String value) {
        if(value==null) return;
        try {
            URI uri=URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException();
        } catch(IllegalArgumentException e) {
            throw new RecruitException(400,"INVALID_URL","HTTP 또는 HTTPS URL을 입력하세요.");
        }
    }
    private static CodeResponse code(CodeValue c) { return c==null ? null : new CodeResponse(c.getCode(),c.getName()); }
    private static CompanyResponse company(Company c) { return new CompanyResponse(c.getId(),c.getName(),c.getCompanyInfoUrl()); }
    private static PostingSummary summary(JobPosting p) {
        return new PostingSummary(p.getId().getSourceCode(),p.getId().getSourcePostingId(),p.getTitle(),company(p.getCompany()),
            p.getActive(),codeList(p.getJobCategories()),p.getRegions().stream().map(RecruitService::code).sorted(java.util.Comparator.comparing(CodeResponse::code)).toList(),codeList(p.getEmploymentTypes()),code(p.getEducationLevel()),p.getEducationNote(),
            code(p.getSalaryUnit()),code(p.getSalaryDecision()),p.getSalaryMinAmount(),p.getSalaryMaxAmount(),code(p.getExperienceType()),p.getApplicationEndAt(),codeList(p.getSpecialties()),codeList(p.getTechStacks()));
    }
    private static List<CodeResponse> codeList(java.util.Set<? extends CodeValue> values) {
        return values.stream().map(RecruitService::code).sorted(java.util.Comparator.comparing(CodeResponse::code)).toList();
    }
    private PostingDetail detail(JobPosting p) {
        JobPostingDetail d=em.find(JobPostingDetail.class,p.getId());
        if(d==null) throw new IllegalStateException("공고 상세 저장 정보가 없습니다.");
        return new PostingDetail(summary(p),d.getSourceUrl(),
            p.getApplicationStartAt(),d.getKeywords(),d.getDescription(),code(p.getDeadlineType()));
    }
}

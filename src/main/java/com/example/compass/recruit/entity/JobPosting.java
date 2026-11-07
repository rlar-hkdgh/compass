package com.example.compass.recruit.entity;

import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="job_posting")
public class JobPosting {
    @EmbeddedId private PostingId id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="source_code",insertable=false,updatable=false) private PostingSource source;
    @Column(nullable=false,length=500) private String title;
    @Column(nullable=false) private boolean active;
    private Instant applicationStartAt;
    private Instant applicationEndAt;
    @ManyToMany(fetch=FetchType.LAZY)
    @JoinTable(name="posting_region",
        joinColumns={@JoinColumn(name="source_code",referencedColumnName="source_code"),
            @JoinColumn(name="source_posting_id",referencedColumnName="source_posting_id")},
        inverseJoinColumns=@JoinColumn(name="region_code",referencedColumnName="code"))
    @OrderBy("code ASC")
    private java.util.Set<Region> regions=new java.util.LinkedHashSet<>();
    @ManyToMany(fetch=FetchType.LAZY)
    @JoinTable(name="posting_specialty",
        joinColumns={@JoinColumn(name="source_code",referencedColumnName="source_code"),
            @JoinColumn(name="source_posting_id",referencedColumnName="source_posting_id")},
        inverseJoinColumns=@JoinColumn(name="specialty_code",referencedColumnName="code"))
    @OrderBy("code ASC")
    private java.util.Set<Specialty> specialties=new java.util.LinkedHashSet<>();
    @ManyToMany(fetch=FetchType.LAZY)
    @JoinTable(name="posting_tech_stack",
        joinColumns={@JoinColumn(name="source_code",referencedColumnName="source_code"),
            @JoinColumn(name="source_posting_id",referencedColumnName="source_posting_id")},
        inverseJoinColumns=@JoinColumn(name="tech_stack_code",referencedColumnName="code"))
    @OrderBy("code ASC")
    private java.util.Set<TechStack> techStacks=new java.util.LinkedHashSet<>();
    @ManyToMany(fetch=FetchType.LAZY)
    @JoinTable(name="posting_employment_type",
        joinColumns={@JoinColumn(name="source_code",referencedColumnName="source_code"),
            @JoinColumn(name="source_posting_id",referencedColumnName="source_posting_id")},
        inverseJoinColumns=@JoinColumn(name="employment_type_code",referencedColumnName="code"))
    @OrderBy("code ASC")
    private java.util.Set<EmploymentType> employmentTypes=new java.util.LinkedHashSet<>();
    @ManyToMany(fetch=FetchType.LAZY)
    @JoinTable(name="posting_job_category",
        joinColumns={@JoinColumn(name="source_code",referencedColumnName="source_code"),
            @JoinColumn(name="source_posting_id",referencedColumnName="source_posting_id")},
        inverseJoinColumns=@JoinColumn(name="job_category_code",referencedColumnName="code"))
    @OrderBy("code ASC")
    private java.util.Set<JobCategory> jobCategories=new java.util.LinkedHashSet<>();
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="education_level_code")
    private EducationLevel educationLevel;
    @Column(columnDefinition="text") private String educationNote;
    private Long salaryMinAmount;
    private Long salaryMaxAmount;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="deadline_type_code")
    private DeadlineType deadlineType;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="salary_unit_code")
    private SalaryUnit salaryUnit;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="salary_decision_code")
    private SalaryDecision salaryDecision;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="experience_type_code")
    private ExperienceType experienceType;
    protected JobPosting() {}
    public JobPosting(PostingId id) { this.id=id; }
    public Instant getApplicationStartAt() { return applicationStartAt; }
    public void setApplicationStartAt(Instant value) { this.applicationStartAt=value; }
    public Instant getApplicationEndAt() { return applicationEndAt; }
    public void setApplicationEndAt(Instant value) { this.applicationEndAt=value; }
    public java.util.Set<Region> getRegions() { return regions; }
    public java.util.Set<Specialty> getSpecialties() { return specialties; }
    public java.util.Set<TechStack> getTechStacks() { return techStacks; }
    public java.util.Set<EmploymentType> getEmploymentTypes() { return employmentTypes; }
    public java.util.Set<JobCategory> getJobCategories() { return jobCategories; }
    public EducationLevel getEducationLevel() { return educationLevel; }
    public void setEducationLevel(EducationLevel value) { this.educationLevel=value; }
    public Long getSalaryMinAmount() { return salaryMinAmount; }
    public void setSalaryMinAmount(Long value) { this.salaryMinAmount=value; }
    public Long getSalaryMaxAmount() { return salaryMaxAmount; }
    public void setSalaryMaxAmount(Long value) { this.salaryMaxAmount=value; }
    public DeadlineType getDeadlineType() { return deadlineType; }
    public void setDeadlineType(DeadlineType value) { this.deadlineType=value; }
    public SalaryUnit getSalaryUnit() { return salaryUnit; }
    public void setSalaryUnit(SalaryUnit value) { this.salaryUnit=value; }
    public SalaryDecision getSalaryDecision() { return salaryDecision; }
    public void setSalaryDecision(SalaryDecision value) { this.salaryDecision=value; }
    public ExperienceType getExperienceType() { return experienceType; }
    public void setExperienceType(ExperienceType value) { this.experienceType=value; }
    public PostingId getId() { return id; }
    public Company getCompany() { return company; }
    public void setCompany(Company value) { this.company=value; }
    public PostingSource getSource() { return source; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title=value; }
    public boolean getActive() { return active; }
    public void setActive(boolean value) { this.active=value; }
    public String getEducationNote() { return educationNote; }
    public void setEducationNote(String value) { this.educationNote=value; }
}

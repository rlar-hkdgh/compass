package com.example.compass.recruit.collection.saramin;


import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SaraminConditionParserTests {
    @Test void educationSeparatesConfirmedNotesAndPreservesAmbiguousConditions() {
        assertThat(SaraminConditionParser.education("대졸(4년제) 이상(예정자 가능)"))
            .isEqualTo(new SaraminConditionParser.Education("8","예정자 가능"));
        var highSchool=SaraminConditionParser.education("고교졸업 이상(예정자 가능) 대학 휴학생 가능");
        assertThat(highSchool.code()).isEqualTo("6");assertThat(highSchool.note()).contains("예정자 가능","휴학생 가능");
        assertThat(SaraminConditionParser.education("학력무관(예정자 가능)").code()).isEqualTo("0");
        assertThat(SaraminConditionParser.education("대졸(2,3년제) 이상").note()).isNull();
        for(String raw:java.util.List.of("학력무관 각 모집 부문에 따라 상이함",
                "대졸(4년제) 이상(예정자 가능) or 실무 3년 이상(전문연구요원)",
                "대졸(2,3년제) 이상(예정자 가능) IT교육 수료(예정)자","새로운 조건")) {
            assertThat(SaraminConditionParser.education(raw)).isEqualTo(new SaraminConditionParser.Education(null,raw));
        }
        assertThat(SaraminConditionParser.education(null)).isEqualTo(new SaraminConditionParser.Education(null,null));
    }
    @Test void salaryParsesExactAmountsWithoutAnnualizing() {
        assertSalary("연봉 3,500 만원 (주 40시간)","ANNUAL",35_000_000L,35_000_000L);
        assertSalary("월급 220 만원 (주 40시간)","MONTHLY",2_200_000L,2_200_000L);
        assertSalary("시급 12,300 원","HOURLY",12_300L,12_300L);
        assertSalary("주급 50만원","WEEKLY",500_000L,500_000L);
        assertSalary("일급 100000원","DAILY",100_000L,100_000L);
        assertSalary("건당 2.5만원","PER_TASK",25_000L,25_000L);
        assertSalary("연봉 1.2억원","ANNUAL",120_000_000L,120_000_000L);
    }
    @Test void salarySupportsRangesAndOneSidedBounds() {
        assertSalary("연봉 3,000~4,000만원","ANNUAL",30_000_000L,40_000_000L);
        assertSalary("연봉 3,000만원 ~ 4,000만원","ANNUAL",30_000_000L,40_000_000L);
        assertSalary("연봉 3,000만원 이상","ANNUAL",30_000_000L,null);
        assertSalary("월급 300만원 이하","MONTHLY",null,3_000_000L);
        assertSalary("연봉 9,000만원~1억원","ANNUAL",90_000_000L,100_000_000L);
    }
    @Test void decisionsDoNotInventAmountsOrUnits() {
        assertThat(SaraminConditionParser.salary("면접 후 결정"))
            .isEqualTo(new SaraminConditionParser.Salary(null,"INTERVIEW",null,null));
        assertThat(SaraminConditionParser.salary("회사내규에 따름"))
            .isEqualTo(new SaraminConditionParser.Salary(null,"COMPANY_POLICY",null,null));
        assertThat(SaraminConditionParser.salary("연봉 면접 후 결정"))
            .isEqualTo(new SaraminConditionParser.Salary("ANNUAL","INTERVIEW",null,null));
    }
    @Test void unconfirmedAmbiguousOrOverflowAmountsRemainUnknown() {
        for(String raw:java.util.List.of("연봉 4,000~3,000만원","연봉 협의 (주 40시간)",
                "연봉 3000만원 또는 면접 후 결정","연봉 999999999999999999억원","연봉 3000",
                "연봉 -3000만원","연봉 3,00만원","연봉 3,000~4,000만원 이상")) {
            var salary=SaraminConditionParser.salary(raw);
            assertThat(salary.minAmount()).as(raw).isNull();assertThat(salary.maxAmount()).as(raw).isNull();
            assertThat(salary.decision()).as(raw).isNull();
        }
        assertThat(SaraminConditionParser.salary(null)).isEqualTo(new SaraminConditionParser.Salary(null,null,null,null));
    }
    private void assertSalary(String raw,String unit,Long min,Long max) {
        assertThat(SaraminConditionParser.salary(raw)).as(raw)
            .isEqualTo(new SaraminConditionParser.Salary(unit,"DISCLOSED",min,max));
    }
}

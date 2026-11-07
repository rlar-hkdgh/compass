package com.example.compass.recruit.collection.saramin;
import com.example.compass.recruit.dto.response.CodeResponse;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SaraminRecruitmentMappingTests {
    private final List<CodeResponse> catalog=List.of(new CodeResponse("1","정규직"),
        new CodeResponse("2","계약직"),new CodeResponse("4","인턴직"),new CodeResponse("9","프리랜서"),
        new CodeResponse("10","계약직 (정규직 전환가능)"),new CodeResponse("11","인턴직 (정규직 전환가능)"),
        new CodeResponse("18","전문계약직"));
    @Test void entryEligibilityRequiresExplicitSummary() {
        assertThat(SaraminRecruitmentMapping.entryExperience("신입")).isEqualTo("1");
        assertThat(SaraminRecruitmentMapping.entryExperience("경력 무관")).isEqualTo("0");
        assertThat(SaraminRecruitmentMapping.entryExperience("경력무관(신입포함)")).isEqualTo("0");
        for(String value:List.of("신입/경력", "신입·경력(년수무관)", "신입·경력 1년 ↑", "신입 · 경력", "신입 경력", "신입/경력 (3년 이상)", "신입/경력 5~8년", "경력/신입"))
            assertThat(SaraminRecruitmentMapping.entryExperience(value)).isEqualTo("3");
        for(String value:List.of("경력", "경력 2년 ↑", "경력 0~3년", "", "협의", "신입 제외", "신입 지원 불가"))
            assertThatThrownBy(()->SaraminRecruitmentMapping.entryExperience(value)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SaraminRecruitmentMapping.entryExperience(null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void probationAndContractDurationDoNotEraseType() {
        for(String value:List.of("정규직 수습기간 3개월","정규직 (수습기간 1개월)","정규직 수습 2개월"))
            assertThat(SaraminRecruitmentMapping.employmentTypes(value,catalog).codes()).containsExactly("1");
        assertThat(SaraminRecruitmentMapping.employmentTypes("계약직 1년",catalog).codes()).containsExactly("2");
        assertThat(SaraminRecruitmentMapping.employmentTypes("전문계약직",catalog).codes()).containsExactly("18");
    }
    @Test void multipleTypesDeduplicateAndConversionIsNotRegularEmployment() {
        for(String value:List.of("계약직, 프리랜서", "계약직·프리랜서", "계약직/프리랜서/계약직"))
            assertThat(SaraminRecruitmentMapping.employmentTypes(value,catalog).codes()).containsExactly("2","9");
        for(String value:List.of("계약직 1년, 정규직 전환 가능", "계약직 (정규직 전환가능)"))
            assertThat(SaraminRecruitmentMapping.employmentTypes(value,catalog).codes()).containsExactly("10");
        assertThat(SaraminRecruitmentMapping.employmentTypes("인턴직 3개월, 정규직 전환 가능",catalog).codes()).containsExactly("11");
    }
    @Test void unknownTypesAreRetainedWithoutInference() {
        var result=SaraminRecruitmentMapping.employmentTypes("정규직, 새로운형태",catalog);
        assertThat(result.codes()).containsExactly("1");assertThat(result.unmatched()).containsExactly("새로운형태");
        assertThat(SaraminRecruitmentMapping.employmentTypes(null,catalog).codes()).isEmpty();
        assertThat(SaraminRecruitmentMapping.employmentTypes("비정규직",catalog).codes()).isEmpty();
    }
}

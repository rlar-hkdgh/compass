package com.example.compass.recruit.collection.saramin;
import com.example.compass.recruit.dto.response.CodeResponse;

import java.util.*;
/** Maps explicit summary values only; never infers eligibility from title or body. */
final class SaraminRecruitmentMapping {
    private SaraminRecruitmentMapping() {}

    static String entryExperience(String value) {
        String text=value==null ? "" : value.replaceAll("[\\s\\u00a0]+", "");
        if(text.equals("경력무관") || text.equals("경력무관(신입포함)")) return "0";
        if(text.equals("신입")) return "1";
        var mixed=java.util.regex.Pattern.compile("^(?:신입(?:[/·ㆍ,]|및)*경력|경력(?:[/·ㆍ,]|및)*신입)(.*)$").matcher(text);
        if(mixed.matches()) {
            String suffix=mixed.group(1);
            if(suffix.isEmpty() || suffix.equals("(년수무관)") || suffix.equals("(연차무관)")
                    || suffix.matches("[（(]?[0-9]+(?:[~～-][0-9]+)?년(?:이상|이하|↑|↓)?[)）]?")) return "3";
        }
        throw new IllegalArgumentException("신입 지원 가능 여부가 확인되지 않은 경력 조건: "+value);
    }

    record EmploymentMapping(List<String> codes,List<String> unmatched) {}
    static EmploymentMapping employmentTypes(String value,List<CodeResponse> catalog) {
        if(value==null || value.isBlank()) return new EmploymentMapping(List.of(),List.of());
        // A conversion option is a single official type, not two current employment types.
        String text=value.replaceAll("(계약직|인턴직)(\\s*[0-9]+\\s*(?:년|개월))?\\s*[,（(]?\\s*정규직\\s*전환\\s*가능\\s*[)）]?", "$1 (정규직 전환가능)");
        var sorted=catalog.stream().sorted(Comparator.comparingInt((CodeResponse c)->c.name().length()).reversed()).toList();
        Set<String> codes=new TreeSet<>();List<String> unmatched=new ArrayList<>();
        for(String part:text.split("[,/·ㆍ\\n]+")) {
            String token=part.trim();if(token.isEmpty()) continue;
            boolean matched=false;
            for(var code:sorted) {
                if(!token.startsWith(code.name())) continue;
                String suffix=token.substring(code.name().length()).trim();
                // Preserve unknown descriptions instead of assigning a similarly named type.
                if(!suffix.isEmpty() && !suffix.matches("(?:[（(]?\\s*수습(?:기간)?\\s*[0-9]+\\s*(?:개월|년)\\s*[)）]?|[0-9]+\\s*(?:년|개월))")) continue;
                codes.add(code.code());matched=true;break;
            }
            if(!matched) unmatched.add(token);
        }
        return new EmploymentMapping(List.copyOf(codes),List.copyOf(unmatched));
    }
}

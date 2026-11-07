package com.example.compass.recruit.collection.saramin;


import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/** Parse explicit source summary values only. Amounts are KRW in the original payment unit. */
public final class SaraminConditionParser {
    private SaraminConditionParser() {}
    public record Education(String code,String note) {}
    public record Salary(String unit,String decision,Long minAmount,Long maxAmount) {}
    private record EducationLabel(String text,String code) {}
    private static final List<EducationLabel> EDUCATION=List.of(
        new EducationLabel("대졸(2,3년제) 이상","7"),new EducationLabel("대졸(4년제) 이상","8"),
        new EducationLabel("고교졸업 이상","6"),new EducationLabel("고졸 이상","6"),
        new EducationLabel("석사졸업 이상","9"),new EducationLabel("박사졸업 이상","5"),
        new EducationLabel("학력무관","0"),new EducationLabel("대졸(2,3년제)","2"),
        new EducationLabel("대졸(4년제)","3"),new EducationLabel("고교졸업","1"),
        new EducationLabel("고졸","1"),new EducationLabel("석사졸업","4"),new EducationLabel("박사졸업","5"));
    public static Education education(String raw) {
        if(raw==null || raw.isBlank()) return new Education(null,null);
        String text=raw.trim();
        // Alternatives and per-position requirements cannot be represented by one level.
        if(Pattern.compile("상이|모집\\s*부문|(?i:\\bor\\b)|또는|혹은|학력.*경력|실무\\s*[0-9]").matcher(text).find())
            return new Education(null,text);
        for(var label:EDUCATION) {
            if(!text.startsWith(label.text())) continue;
            String note=text.substring(label.text().length()).trim();
            if(note.isEmpty()) return new Education(label.code(),null);
            // Only confirmed eligibility annotations are separated; unknown conditions stay intact.
            String remainder=note.replaceAll("[（(]?(?:졸업\\s*)?예정자\\s*가능[)）]?", "")
                .replaceAll("대학\\s*휴학생\\s*가능", "").replaceAll("[\\s,·]+", "");
            if(!remainder.isEmpty()) return new Education(null,text);
            if(note.startsWith("(") && note.endsWith(")") && note.indexOf(')')==note.length()-1)
                note=note.substring(1,note.length()-1).trim();
            return new Education(label.code(),note);
        }
        return new Education(null,text);
    }

    private static final Pattern UNIT=Pattern.compile("^(연봉|월급|주급|일급|시급|건당)\\s*");
    private static final Pattern AMOUNTS=Pattern.compile(
        "^([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)(억\\s*원|억원|억|만\\s*원|만원|만|원)?"
        +"(?:\\s*[~～–-]\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)(억\\s*원|억원|억|만\\s*원|만원|만|원)?)?"
        +"\\s*(이상|이하)?$");
    public static Salary salary(String raw) {
        if(raw==null || raw.isBlank()) return new Salary(null,null,null,null);
        String text=raw.trim();String unit=null;
        var prefix=UNIT.matcher(text);
        if(prefix.find()) {
            unit=switch(prefix.group(1)) {
                case "연봉"->"ANNUAL";case "월급"->"MONTHLY";case "주급"->"WEEKLY";
                case "일급"->"DAILY";case "시급"->"HOURLY";default->"PER_TASK";
            };
            text=text.substring(prefix.end()).trim();
        }
        String normalized=text.replaceAll("\\s+", "");
        if(normalized.equals("면접후결정")) return new Salary(unit,"INTERVIEW",null,null);
        if(normalized.equals("회사내규에따름")) return new Salary(unit,"COMPANY_POLICY",null,null);
        if(unit==null) return new Salary(null,null,null,null);
        // Strip only the known working-hours annotation; never parse its number as salary.
        text=text.replaceFirst("\\s*[（(]\\s*주\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?\\s*시간\\s*[)）]$", "")
            .replaceAll("\\s+", "");
        var values=AMOUNTS.matcher(text);
        if(!values.matches()) return new Salary(unit,null,null,null);
        String firstScale=values.group(2),secondScale=values.group(4);
        if(values.group(3)!=null && firstScale==null) firstScale=secondScale;
        if(firstScale==null) return new Salary(unit,null,null,null);
        try {
            long min=won(values.group(1),firstScale);
            Long max=values.group(3)==null ? min : won(values.group(3),secondScale==null ? firstScale : secondScale);
            String qualifier=values.group(5);
            if(values.group(3)!=null && qualifier!=null) return new Salary(unit,null,null,null);
            if("이상".equals(qualifier)) max=null;
            if("이하".equals(qualifier)) return new Salary(unit,"DISCLOSED",null,min);
            if(max!=null && min>max) return new Salary(unit,null,null,null);
            return new Salary(unit,"DISCLOSED",min,max);
        } catch(ArithmeticException e) { return new Salary(unit,null,null,null); }
    }
    private static long won(String number,String scale) {
        long multiplier=scale.startsWith("억") ? 100_000_000L : scale.startsWith("만") ? 10_000L : 1L;
        return new BigDecimal(number.replace(",", "")).multiply(BigDecimal.valueOf(multiplier)).longValueExact();
    }
}

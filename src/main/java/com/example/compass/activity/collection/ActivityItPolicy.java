package com.example.compass.activity.collection;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ActivityItPolicy {
    private ActivityItPolicy() {}
    private static final Pattern IT = Pattern.compile(
        "프로그래밍|알고리즘|자료구조|데이터\\s*베이스|운영체제|컴퓨터\\s*(구조|네트워크)|정보\\s*보안|정보\\s*보호|"
        + "인공지능|기계\\s*학습|머신\\s*러닝|딥\\s*러닝|소프트웨어|웹\\s*개발|앱\\s*개발|백엔드|프론트엔드|"
        + "웹서비스\\s*개발|클라우드|데이터\\s*(분석|엔지니어|사이언스)|정보처리|리눅스|네트워크관리|"
        + "(?<![a-z0-9])(?:python|java|javascript|typescript|sql[dp]?|adsp|adp|devops|mlops|"
        + "database|algorithm|linux|computer science|computer networks|operating systems|data structures|machine learning|"
        + "deep learning|software|programming|cybersecurity|backend|frontend)(?![a-z0-9])",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTRA_IT = Pattern.compile("코딩|해커톤|(?<![a-z0-9])(?:ai|sw|iot|llm|rag|coding|hackathon)(?![a-z0-9])", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROLE = Pattern.compile(
        "개발자|개발\\s*(인턴|엔지니어|업무)|백엔드|프론트엔드|데이터\\s*(분석|엔지니어|사이언)|"
        + "정보\\s*(보안|보호)|보안\\s*(분석|엔지니어|담당)|인프라\\s*(엔지니어|운영)|시스템\\s*엔지니어|"
        + "(?:software|backend|frontend|data|security|cloud)\\s*(?:engineer|developer|analyst)|프로그래밍",
        Pattern.CASE_INSENSITIVE);
    /** Only the role field, never employer introduction or general application instructions. */
    public static boolean acceptsInternshipRole(String role) {
        return role != null && accepts("INTERNSHIP", role, role);
    }
    public static boolean accepts(String type, String title, String evidence) {
        if (title == null || evidence == null || evidence.isBlank()) return false;
        String text = (title + "\n" + evidence).toLowerCase(Locale.ROOT);
        return (IT.matcher(text).find() || "EXTRACURRICULAR".equals(type) && EXTRA_IT.matcher(text).find())
            && (!"INTERNSHIP".equals(type) || ROLE.matcher(text).find());
    }
}

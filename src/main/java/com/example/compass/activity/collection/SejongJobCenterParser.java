package com.example.compass.activity.collection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** Parser for public recommendation listings; never submits login/application forms. */
public final class SejongJobCenterParser {
    private SejongJobCenterParser() {}
    static final String ORIGIN = "https://udream.sejong.ac.kr";
    private static final Pattern LINK = Pattern.compile("^javascript:detailView\\('([A-Fa-f0-9]{64}(?:[A-Fa-f0-9]{16})?)',\\s*'([0-9]+)'\\);?$");
    public record Posting(String id, String token, String title) {
        public Posting {
            if (id == null || !id.matches("[0-9]+") || token == null || !token.matches("[A-Fa-f0-9]{64}(?:[A-Fa-f0-9]{16})?"))
                throw new IllegalArgumentException("Invalid recommendation identity");
        }
        public URI uri() { return URI.create(ORIGIN + "/Recruit/RecruitView.aspx?rcdx=" + token + "&modalChk=Y"); }
    }
    public record Listing(List<Posting> postings, Set<String> allIds, int lastLinkedPage) {}
    public static URI listUri(int page) {
        if (page < 1) throw new IllegalArgumentException("Page must be positive");
        return URI.create(ORIGIN + "/Recruit/RecruitSchoolList.aspx?link=i&rp=" + page);
    }
    static void validateUri(URI uri) {
        if (!uri.toString().matches("https://udream\\.sejong\\.ac\\.kr/Recruit/(?:RecruitSchoolList\\.aspx\\?link=i&rp=[1-9][0-9]*|RecruitView\\.aspx\\?rcdx=[A-Fa-f0-9]{64}(?:[A-Fa-f0-9]{16})?&modalChk=Y)"))
            throw new IllegalArgumentException("Only recommendation list/detail URLs are accepted");
    }
    public static Listing listing(String html, int page) {
        var doc = Jsoup.parse(html);
        var rows = doc.select("tr[id^=hcTr]").stream().filter(r -> r.id().matches("hcTr[0-9]+")).toList();
        if (rows.isEmpty()) throw new IllegalArgumentException("Recommendation listing missing or login required");
        Set<String> ids = new LinkedHashSet<>();
        Map<String, Posting> result = new LinkedHashMap<>();
        for (Element row : rows) {
            var links = row.select("a[href^=javascript:detailView]");
            if (links.size() != 1) throw new IllegalArgumentException("Recommendation row structure changed");
            var match = LINK.matcher(links.first().attr("href"));
            if (!match.matches()) throw new IllegalArgumentException("Recommendation detail link changed");
            ids.add(match.group(2));
            String title = links.first().text();
            // Do not use company industry as role evidence. Detailed role is checked after fetching.
            if (title.contains("인턴")) {
                Posting p = new Posting(match.group(2), match.group(1), title);
                result.putIfAbsent(p.id(), p);
            }
        }
        int last = page;
        for (Element link : doc.select("a[onclick]")) {
            String action = link.attr("onclick");
            if (!action.startsWith("hcSubmit('/Recruit/RecruitSchoolList.aspx?link=i&rp=")) continue;
            var match = Pattern.compile("[?&]rp=([0-9]+)").matcher(action);
            if (match.find()) last = Math.max(last, Integer.parseInt(match.group(1)));
        }
        return new Listing(List.copyOf(result.values()), Set.copyOf(ids), last);
    }
    public static Optional<CollectedActivity> detail(Posting posting, String html) {
        var doc = Jsoup.parse(html, posting.uri().toString());
        String token = one(doc, "input#rcdx").attr("value");
        if (!posting.token().equals(token)) throw new IllegalArgumentException("Detail identity mismatch");
        String title = one(doc, "#Title").text();
        String company = one(doc, "#bizname").text();
        String role = one(doc, "#_job").text();
        String employment = one(doc, "#worktype3").text();
        String body = one(doc, "#jobdetail1").text();
        String deadline = one(doc, "#edate").text();
        if (!employment.contains("인턴")) return Optional.empty();
        if (!ActivityItPolicy.acceptsInternshipRole(role)) return Optional.empty();
        if (title.isBlank() || title.length() > 500 || company.isBlank() || company.length() > 255
            || body.length() < 20 || body.length() > 200_000)
            throw new IllegalArgumentException("Incomplete recommendation detail");
        String content = "공고: " + title + "\n회사명: " + company + "\n경력여부: " + employment
            + "\n직무명: " + role + "\n접수마감일: " + deadline + "\n" + body;
        // Numeric listing ID is stable across encrypted URL variants and title edits.
        var period=ActivityDates.internship(body);
        return Optional.of(new CollectedActivity("INTERNSHIP", "SEJONG_JOB_CENTER", "udream:" + posting.id(),
            posting.uri().toString(), title, company, content, role,Map.of(),period.start(),period.end(),null,ActivityDates.deadline(deadline)));
    }
    private static Element one(Element root, String selector) {
        var matches = root.select(selector);
        if (matches.size() != 1) throw new IllegalArgumentException("Missing/ambiguous career center field: " + selector);
        return matches.first();
    }
}

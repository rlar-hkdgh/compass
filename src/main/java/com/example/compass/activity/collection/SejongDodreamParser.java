package com.example.compass.activity.collection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

public final class SejongDodreamParser {
    private SejongDodreamParser() {}
    static final String ORIGIN = "https://do.sejong.ac.kr";
    private static final Pattern IT_PROGRAM = Pattern.compile("코딩|해커톤|프로그래밍|알고리즘|(?<![a-z0-9])(?:ai|sw|iot|llm|rag|coding|hackathon)(?![a-z0-9])", Pattern.CASE_INSENSITIVE);
    public record Program(String id, String title) {
        public Program {
            if (id == null || !id.matches("[1-9][0-9]{0,9}")) throw new IllegalArgumentException("Invalid program ID");
        }
        public URI uri() { return URI.create(ORIGIN + "/ko/program/all/view/" + id); }
    }
    public record Listing(List<Program> programs, Set<String> allIds, boolean hasNext) {}
    public static URI listUri(int page) {
        if (page < 1) throw new IllegalArgumentException("Page must be positive");
        return URI.create(ORIGIN + "/ko/program/all/list/all/1/" + page);
    }
    static void validateUri(URI uri) {
        if (!uri.toString().matches("https://do\\.sejong\\.ac\\.kr/ko/program/all/(?:list/all/1/[1-9][0-9]*|view/[1-9][0-9]{0,9})"))
            throw new IllegalArgumentException("Only Dodream list/detail URLs are accepted");
    }
    static boolean isIt(String title, String description) {
        description = description.replaceAll("\\[[^\\]]*(?:중심대학|추진단|사업단|센터)[^\\]]*\\]", "");
        title = title.replaceAll("\\[[^\\]]*(?:중심대학|추진단|사업단|센터)[^\\]]*\\]", "");
        return ActivityItPolicy.accepts("EXTRACURRICULAR", title, description)
            || IT_PROGRAM.matcher(title + "\n" + description).find();
    }
    public static Listing listing(String html, int page) {
        var doc = Jsoup.parse(html, listUri(page).toString());
        Element context = one(doc, "[data-module=eco][data-context=program][data-view=list]");
        one(context, "#ModuleEcoProgramListForm");
        var cards = one(context, "ul[data-role=list]").select("[data-module=eco][data-role=item]");
        if (cards.isEmpty()) throw new IllegalArgumentException("Missing Dodream listing");
        Element pagination = one(doc, "[data-role=pagination][data-page]");
        if (!Integer.toString(page).equals(pagination.attr("data-page")))
            throw new IllegalArgumentException("Dodream page number mismatch");
        Set<String> ids = new LinkedHashSet<>();
        List<Program> result = new ArrayList<>();
        for (Element card : cards) {
            Element link = one(card, "a[data-idx]");
            String id = link.attr("data-idx");
            var titles = link.select(".title").eachText().stream().distinct().toList();
            if (titles.size() != 1 || titles.get(0).isBlank()) throw new IllegalArgumentException("Dodream card title missing/ambiguous");
            var program = new Program(id, titles.get(0));
            if (!program.uri().toString().equals(link.absUrl("href")))
                throw new IllegalArgumentException("Dodream card identity mismatch");
            if (ids.add(id) && isIt(program.title(), program.title())) result.add(program);
        }
        boolean next = false;
        for (Element link : pagination.select("a[href]")) {
            String href = link.absUrl("href");
            if (href.equals(ORIGIN + "/ko/program/all/list/all/1/" + ((long) page + 1))) next = true;
        }
        return new Listing(List.copyOf(result), Set.copyOf(ids), next);
    }
    public static Optional<CollectedActivity> detail(Program program, String html) {
        var doc = Jsoup.parse(html, program.uri().toString());
        Element root = one(doc, "#ModuleEcoProgramView");
        Element item = one(root, "[data-module=eco][data-role=itemview][data-pidx]");
        if (!program.id().equals(item.attr("data-pidx"))) throw new IllegalArgumentException("Dodream detail identity mismatch");
        String title = one(item, ".title h4").text();
        String target = one(item, ".title li.target > span").text();
        String provider = one(item, ".info .department > a").text();
        String body = one(root, ".description").text();
        if (title.isBlank() || title.length() > 500 || provider.isBlank() || provider.length() > 255
            || body.length() < 20 || body.length() > 200_000) throw new IllegalArgumentException("Incomplete Dodream detail");
        // A graduate-only or staff-only course is not an undergraduate recommendation.
        if (!(target.contains("전체 학생") || target.contains("학부생") || target.contains("대학생") || target.equals("재학생")))
            return Optional.empty();
        if (!isIt(title, body)) return Optional.empty();
        String content = "프로그램: " + title + "\n운영기관: " + provider + "\n모집대상: " + target + "\n" + body;
        var dates=ActivityDates.dodream(root,item);
        return Optional.of(new CollectedActivity("EXTRACURRICULAR", "SEJONG_DODREAM", program.id(),
            program.uri().toString(), title, provider, content, title + "\n" + body, Map.of(),dates.period().start(),dates.period().end(),dates.applicationStart(),dates.applicationEnd()));
    }
    private static Element one(Element root, String selector) {
        var matches = root.select(selector);
        if (matches.size() != 1) throw new IllegalArgumentException("Missing/ambiguous Dodream field: " + selector);
        return matches.first();
    }
}

package com.example.compass.activity.collection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class HtmlFixtureHygieneTests {
 @Test void fixturesContainNoExecutableScriptsTrackingTokenOrEmbeddedImages()throws Exception{
  var json=JsonMapper.builder().build();
  try(var files=Files.walk(Path.of("src/test/resources/activity"))){
   var paths=files.filter(p->p.toString().endsWith(".html")).toList();
   assertThat(paths).isNotEmpty();
   for(var path:paths){
    String html=Files.readString(path);var doc=Jsoup.parse(html);
    assertThat(html).as(path.toString()).doesNotContain("projectToken");
    assertThat(doc.select("style,iframe,object,embed")).as(path.toString()).isEmpty();
    for(var script:doc.select("script")){
     assertThat(script.attr("type")).as(path.toString()).isEqualTo("application/ld+json");
     assertThat(script.hasAttr("src")).isFalse();
     var identity=json.readTree(script.data());
     assertThat(identity.get("@type").asText()).isEqualTo("Product");
     assertThat(identity.get("url").asText()).startsWith("https://soldesk.com/shop_view/?idx=");
     assertThat(identity.size()).isEqualTo(2);
    }
    for(var element:doc.getAllElements())for(var attr:element.attributes())
     assertThat(attr.getValue().toLowerCase(Locale.ROOT)).as(path+" attribute "+attr.getKey()).doesNotContain("data:image/");
   }
  }
 }
}

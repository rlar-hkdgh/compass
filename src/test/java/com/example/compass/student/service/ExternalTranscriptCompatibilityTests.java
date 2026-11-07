package com.example.compass.student.service;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import jakarta.validation.Validation;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

/** Optional read-only compatibility check. Never copies personal transcript contents into fixtures. */
@EnabledIfEnvironmentVariable(named="COMPASS_TEST_TRANSCRIPT",matches=".+")
class ExternalTranscriptCompatibilityTests {
    @Test void suppliedPortalExportIsRecognizedWithoutRowErrors() throws Exception {
        var source=Path.of(System.getenv("COMPASS_TEST_TRANSCRIPT"));
        byte[] before=Files.readAllBytes(source);
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var parsed=new TranscriptWorkbookParser(factory.getValidator()).parse(before);
            assertThat(parsed.rows().size()).isGreaterThan(0);
            assertThat(parsed.warnings()).isEmpty();
            // Assert only aggregate validity; do not include private row values in assertion messages.
            assertThat(parsed.rows().stream().allMatch(r->r.errors().isEmpty())).isTrue();
            assertThat(parsed.rows().stream().allMatch(r->r.course().courseNumber()!=null)).isTrue();
        }
        assertThat(java.util.Arrays.equals(before,Files.readAllBytes(source))).isTrue();
    }
}

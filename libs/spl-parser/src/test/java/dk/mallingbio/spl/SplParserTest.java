package dk.mallingbio.spl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SplParserTest {

    private final SplParser parser = new SplParser();

    @Test
    void parse_xml_with_simple_cue_nodes() {
        String xml = """
                <spl>
                  <cues>
                    <cue name="loftlys skelne" offsetSeconds="5400"/>
                    <cue name="doors-open" offsetSeconds="5500"/>
                  </cues>
                </spl>
                """;

        ParsedSpl parsed = parser.parseXml(xml);

        assertNotNull(parsed);
        assertEquals(2, parsed.cues().size());

        SplCue first = parsed.cues().get(0);
        assertEquals("cue", first.nodeName());
        assertEquals("loftlys skelne", first.cueName());
        assertEquals("5400", first.rawOffset());
        assertEquals(5400L, first.offsetSeconds());
    }

    @Test
    void parse_xml_finds_first_cue_by_name_contains_case_insensitive() {
        String xml = """
                <spl>
                  <cues>
                    <cue name="Loftlys Skelne" offsetSeconds="5400"/>
                    <cue name="something else" offsetSeconds="100"/>
                  </cues>
                </spl>
                """;

        ParsedSpl parsed = parser.parseXml(xml);

        Optional<SplCue> cue = parsed.findFirstCueByNameContains("loftlys");

        assertTrue(cue.isPresent());
        assertEquals("Loftlys Skelne", cue.get().cueName());
        assertEquals(5400L, cue.get().offsetSeconds());
    }

    @Test
    void parse_base64_decodes_and_parses_xml() {
        String xml = """
                <spl>
                  <cues>
                    <cue name="loftlys skelne" offsetSeconds="5400"/>
                  </cues>
                </spl>
                """;

        String base64 = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));

        ParsedSpl parsed = parser.parseBase64(base64);

        assertNotNull(parsed);
        assertEquals(1, parsed.cues().size());
        assertEquals("loftlys skelne", parsed.cues().get(0).cueName());
        assertEquals(5400L, parsed.cues().get(0).offsetSeconds());
    }

    @Test
    void parse_xml_supports_label_and_offset_attributes() {
        String xml = """
                <spl>
                  <cues>
                    <cue label="loftlys skelne" offset="1234"/>
                  </cues>
                </spl>
                """;

        ParsedSpl parsed = parser.parseXml(xml);

        assertEquals(1, parsed.cues().size());

        SplCue cue = parsed.cues().get(0);
        assertEquals("loftlys skelne", cue.cueName());
        assertEquals("1234", cue.rawOffset());
        assertEquals(1234L, cue.offsetSeconds());
    }

    @Test
    void parse_xml_supports_child_elements_for_name_and_offset() {
        String xml = """
                <spl>
                  <cues>
                    <cue>
                      <name>loftlys skelne</name>
                      <offsetSeconds>3210</offsetSeconds>
                    </cue>
                  </cues>
                </spl>
                """;

        ParsedSpl parsed = parser.parseXml(xml);

        assertEquals(1, parsed.cues().size());

        SplCue cue = parsed.cues().get(0);
        assertEquals("loftlys skelne", cue.cueName());
        assertEquals("3210", cue.rawOffset());
        assertEquals(3210L, cue.offsetSeconds());
    }

    @Test
    void parse_xml_parses_hh_mm_ss_offset() {
        String xml = """
                <spl>
                  <cues>
                    <cue name="loftlys skelne" offset="01:30:00"/>
                  </cues>
                </spl>
                """;

        ParsedSpl parsed = parser.parseXml(xml);

        SplCue cue = parsed.cues().get(0);
        assertEquals("01:30:00", cue.rawOffset());
        assertEquals(5400L, cue.offsetSeconds());
    }

    @Test
    void parse_xml_parses_mm_ss_offset() {
        String xml = """
                <spl>
                  <cues>
                    <cue name="mid cue" offset="15:20"/>
                  </cues>
                </spl>
                """;

        ParsedSpl parsed = parser.parseXml(xml);

        SplCue cue = parsed.cues().get(0);
        assertEquals("15:20", cue.rawOffset());
        assertEquals(920L, cue.offsetSeconds());
    }

    @Test
    void parse_xml_parses_iso_8601_duration_offset() {
        String xml = """
                <spl>
                  <cues>
                    <cue name="iso cue" offset="PT1H30M"/>
                  </cues>
                </spl>
                """;

        ParsedSpl parsed = parser.parseXml(xml);

        SplCue cue = parsed.cues().get(0);
        assertEquals("PT1H30M", cue.rawOffset());
        assertEquals(5400L, cue.offsetSeconds());
    }

    @Test
    void parse_xml_keeps_null_offset_seconds_for_unknown_offset_format() {
        String xml = """
                <spl>
                  <cues>
                    <cue name="mystery cue" offset="frame:12345"/>
                  </cues>
                </spl>
                """;

        ParsedSpl parsed = parser.parseXml(xml);

        SplCue cue = parsed.cues().get(0);
        assertEquals("frame:12345", cue.rawOffset());
        assertNull(cue.offsetSeconds());
    }

    @Test
    void parse_xml_ignores_cue_like_nodes_without_name_and_offset() {
        String xml = """
                <spl>
                  <cues>
                    <cue/>
                    <cue name="usable cue" offsetSeconds="120"/>
                  </cues>
                </spl>
                """;

        ParsedSpl parsed = parser.parseXml(xml);

        assertEquals(1, parsed.cues().size());
        assertEquals("usable cue", parsed.cues().get(0).cueName());
    }

    @Test
    void decode_base64_to_xml_returns_original_xml() {
        String xml = "<spl><cues><cue name=\"x\" offsetSeconds=\"1\"/></cues></spl>";
        String base64 = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));

        String decoded = parser.decodeBase64ToXml(base64);

        assertEquals(xml, decoded);
    }

    @Test
    void parse_invalid_xml_throws_illegal_argument_exception() {
        String invalidXml = "<spl><cues><cue></spl>";

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parseXml(invalidXml)
        );

        assertTrue(ex.getMessage().contains("Failed to parse SPL XML"));
    }

    @Test
    void find_first_cue_by_name_contains_returns_empty_when_not_found() {
        String xml = """
                <spl>
                  <cues>
                    <cue name="something else" offsetSeconds="100"/>
                  </cues>
                </spl>
                """;

        ParsedSpl parsed = parser.parseXml(xml);

        Optional<SplCue> cue = parsed.findFirstCueByNameContains("loftlys");

        assertTrue(cue.isEmpty());
    }
}
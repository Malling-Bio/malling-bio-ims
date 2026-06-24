package dk.mallingbio.spl;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class SplParser {

    public String decodeBase64ToXml(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public ParsedSpl parseBase64(String base64) {
        String xml = decodeBase64ToXml(base64);
        return parseXml(xml);
    }

    public ParsedSpl parseXml(String xml) {
        try {
            Document document = parseDocument(xml);

            List<SplCue> cues = new ArrayList<>();

            XPath xpath = XPathFactory.newInstance().newXPath();

            // Første heuristik:
            // find alle noder hvor local-name indeholder "cue"
            NodeList cueNodes = (NodeList) xpath.evaluate(
                    "//*[contains(translate(local-name(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'cue')]",
                    document,
                    XPathConstants.NODESET
            );

            for (int i = 0; i < cueNodes.getLength(); i++) {
                Node node = cueNodes.item(i);
                if (node instanceof Element element) {
                    SplCue cue = toCue(element);
                    if (cue != null) {
                        cues.add(cue);
                    }
                }
            }

            return new ParsedSpl(xml, List.copyOf(cues));

        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse SPL XML", ex);
        }
    }

    private Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private SplCue toCue(Element element) {
        String nodeName = element.getTagName();

        String cueName = firstNonBlank(
                attr(element, "name"),
                attr(element, "label"),
                attr(element, "id"),
                childText(element, "name"),
                childText(element, "label"),
                childText(element, "description"),
                childText(element, "title")
        );

        String rawOffset = firstNonBlank(
                attr(element, "offsetSeconds"),
                attr(element, "offset"),
                attr(element, "time"),
                attr(element, "start"),
                attr(element, "position"),
                childText(element, "offsetSeconds"),
                childText(element, "offset"),
                childText(element, "time"),
                childText(element, "start"),
                childText(element, "position")
        );

        Long offsetSeconds = parseOffsetToSeconds(rawOffset).orElse(null);

        // Hvis vi hverken har navn eller offset, er noden for uinteressant i første version
        if (cueName == null && rawOffset == null) {
            return null;
        }

        return new SplCue(
                nodeName,
                cueName,
                rawOffset,
                offsetSeconds
        );
    }

    private Optional<Long> parseOffsetToSeconds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        String value = raw.trim();

        // 1) Rent heltal -> antag sekunder i første version
        if (value.matches("^\\d+$")) {
            try {
                return Optional.of(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }

        // 2) HH:mm:ss eller mm:ss
        if (value.matches("^\\d{1,2}:\\d{2}(:\\d{2})?$")) {
            String[] parts = value.split(":");
            try {
                if (parts.length == 2) {
                    long minutes = Long.parseLong(parts[0]);
                    long seconds = Long.parseLong(parts[1]);
                    return Optional.of(minutes * 60 + seconds);
                }
                if (parts.length == 3) {
                    long hours = Long.parseLong(parts[0]);
                    long minutes = Long.parseLong(parts[1]);
                    long seconds = Long.parseLong(parts[2]);
                    return Optional.of(hours * 3600 + minutes * 60 + seconds);
                }
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }

        // 3) ISO-8601 duration, fx PT1H23M10S
        try {
            Duration duration = Duration.parse(value);
            return Optional.of(duration.getSeconds());
        } catch (Exception ignored) {
            // ignore
        }

        return Optional.empty();
    }

    private String attr(Element element, String name) {
        String value = element.getAttribute(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String childText(Element parent, String wantedLocalName) {
        NodeList children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element) {
                String localName = element.getLocalName() != null ? element.getLocalName() : element.getTagName();
                if (localName.equalsIgnoreCase(wantedLocalName)) {
                    String text = element.getTextContent();
                    if (text != null && !text.isBlank()) {
                        return text.trim();
                    }
                }
            }
        }

        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

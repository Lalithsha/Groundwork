package com.groundwork.evidence.application;

import com.groundwork.evidence.domain.ReleaseRecord;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ReleaseRecordExportService {
    public String html(ReleaseRecord release, ReleaseRecordService.Verification verification) {
        StringBuilder body = new StringBuilder();
        body.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            .append("<title>").append(escape(release.name())).append(" · Groundwork evidence</title>")
            .append("<style>body{font:15px system-ui;max-width:980px;margin:40px auto;padding:0 24px;color:#17211b}")
            .append("h1,h2{letter-spacing:-.025em}.meta{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px}")
            .append(".card{border:1px solid #d8e0da;border-radius:12px;padding:16px;margin:12px 0}.pass{color:#11643b}.fail{color:#a62d2d}")
            .append("code{overflow-wrap:anywhere}li{margin:.4rem 0}</style></head><body>")
            .append("<p>GROUNDWORK / IMMUTABLE RELEASE EVIDENCE</p><h1>").append(escape(release.name())).append("</h1>")
            .append("<div class=\"meta\"><div class=\"card\"><b>Repository</b><br>").append(escape(release.repositoryFullName())).append("</div>")
            .append("<div class=\"card\"><b>Head</b><br><code>").append(escape(release.headRef())).append("</code></div>")
            .append("<div class=\"card\"><b>Frozen</b><br>").append(release.frozenAt()).append("</div>")
            .append("<div class=\"card\"><b>Integrity</b><br><span class=\"")
            .append(verification.valid() ? "pass\">VERIFIED" : "fail\">MODIFIED")
            .append("</span></div></div><h2>Manifest digest</h2><div class=\"card\"><code>")
            .append(escape(release.manifestHash())).append("</code></div><h2>Changes</h2>");
        for (Map<String, Object> change : maps(release.manifest().get("changes"))) {
            body.append("<article class=\"card\"><h3>").append(escape(String.valueOf(change.get("title"))))
                .append("</h3><p><code>").append(escape(String.valueOf(change.get("headSha")))).append("</code></p><ul>");
            for (Map<String, Object> finding : maps(change.get("findings"))) {
                body.append("<li><b>").append(escape(String.valueOf(finding.get("severity")))).append(" · ")
                    .append(escape(String.valueOf(finding.get("category")))).append(":</b> ")
                    .append(escape(String.valueOf(finding.get("statement")))).append("</li>");
            }
            body.append("</ul></article>");
        }
        return body.append("<footer><p>Generated from a frozen Groundwork manifest. Verify the SHA-256 digest through the API.</p></footer></body></html>").toString();
    }

    public byte[] pdf(ReleaseRecord release, ReleaseRecordService.Verification verification) {
        List<String> lines = new ArrayList<>();
        lines.add("GROUNDWORK / IMMUTABLE RELEASE EVIDENCE");
        lines.add(release.name());
        lines.add("Repository: " + release.repositoryFullName());
        lines.add("Head: " + release.headRef());
        lines.add("Frozen: " + release.frozenAt());
        lines.add("Integrity: " + (verification.valid() ? "VERIFIED" : "MODIFIED"));
        lines.add("Manifest SHA-256: " + release.manifestHash());
        lines.add("");
        for (Map<String, Object> change : maps(release.manifest().get("changes"))) {
            lines.add("CHANGE · " + change.get("title"));
            lines.add("Head SHA: " + change.get("headSha"));
            for (Map<String, Object> finding : maps(change.get("findings"))) {
                lines.add(finding.get("severity") + " · " + finding.get("category") + " · " + finding.get("statement"));
            }
            lines.add("");
        }
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDPage page = null;
            PDPageContentStream stream = null;
            float y = 0;
            for (String raw : lines) {
                for (String line : wrap(raw, 96)) {
                    if (page == null || y < 54) {
                        if (stream != null) stream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        stream = new PDPageContentStream(document, page);
                        y = page.getMediaBox().getHeight() - 54;
                    }
                    stream.beginText();
                    stream.setFont(line.startsWith("GROUNDWORK") || line.startsWith("CHANGE ·") ? bold : regular,
                        line.startsWith("GROUNDWORK") ? 13 : 9.5f);
                    stream.newLineAtOffset(54, y);
                    stream.showText(ascii(line));
                    stream.endText();
                    y -= line.isBlank() ? 8 : 14;
                }
            }
            if (stream != null) stream.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Release PDF export failed", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }
    private static List<String> wrap(String value, int width) {
        if (value == null || value.isBlank()) return List.of("");
        List<String> result = new ArrayList<>();
        String remaining = value;
        while (remaining.length() > width) {
            int split = remaining.lastIndexOf(' ', width);
            if (split < width / 2) split = width;
            result.add(remaining.substring(0, split));
            remaining = remaining.substring(split).trim();
        }
        result.add(remaining);
        return result;
    }
    private static String ascii(String value) { return value.replaceAll("[^\\x20-\\x7E]", "-"); }
    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

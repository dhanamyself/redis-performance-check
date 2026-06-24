package com.azure.redisperf.service;

import com.azure.redisperf.model.TestResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Writes a standalone, self-contained HTML report for a completed run and
 * lists previously generated reports. The report embeds the result JSON plus
 * the shared rendering script so it can be opened directly from disk.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${redisperf.reports-dir:./reports}")
    private String reportsDir;

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Paths.get(reportsDir));
        log.info("Reports directory: {}", Paths.get(reportsDir).toAbsolutePath());
    }

    public Path reportsPath() {
        return Paths.get(reportsDir);
    }

    public void writeReport(TestResult result, String fileName) throws IOException {
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        String renderJs = readClasspath("static/render.js");
        String template = readClasspath("report-template.html");

        String html = template
                .replace("/*__RENDER_JS__*/", renderJs)
                .replace("\"__RESULT_JSON__\"", json)
                .replace("__TITLE__", escape(result.getLabel() + " — " + result.getRunId()));

        Path out = reportsPath().resolve(fileName);
        Files.writeString(out, html, StandardCharsets.UTF_8);
        log.info("Wrote report {}", out.toAbsolutePath());
    }

    public List<ReportFile> listReports() throws IOException {
        List<ReportFile> files = new ArrayList<>();
        if (!Files.isDirectory(reportsPath())) return files;
        try (var stream = Files.list(reportsPath())) {
            stream.filter(p -> p.getFileName().toString().endsWith(".html"))
                    .forEach(p -> {
                        try {
                            files.add(new ReportFile(p.getFileName().toString(),
                                    Files.size(p), Files.getLastModifiedTime(p).toMillis()));
                        } catch (IOException ignored) { }
                    });
        }
        files.sort(Comparator.comparingLong(ReportFile::lastModified).reversed());
        return files;
    }

    public Path resolveReport(String fileName) {
        // Prevent path traversal — only allow plain file names.
        String safe = Paths.get(fileName).getFileName().toString();
        return reportsPath().resolve(safe);
    }

    private String readClasspath(String path) throws IOException {
        ClassPathResource res = new ClassPathResource(path);
        try (var in = res.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("<", "&lt;").replace(">", "&gt;");
    }

    public record ReportFile(String name, long size, long lastModified) {}
}

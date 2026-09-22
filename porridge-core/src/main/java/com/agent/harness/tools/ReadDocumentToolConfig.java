package com.agent.harness.tools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.ai.document.Document;
import java.util.List;
import java.util.function.Function;

@Configuration
public class ReadDocumentToolConfig {

    public record ReadDocRequest(String path) {}

    @Bean
    @Description("Extracts readable text from complex binary files such as PDF, XLSX, DOCX, and PPTX.")
    public Function<ReadDocRequest, String> readDocumentTool() {
        return req -> {
            try {
                TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(req.path()));
                List<Document> docs = reader.get();
                StringBuilder sb = new StringBuilder();
                for (Document doc : docs) {
                    sb.append(doc.getText()).append("\n");
                }
                
                String content = sb.toString();
                if (content.length() > 50000) {
                    content = content.substring(0, 50000) + "\n\n...[TRUNCATED due to length]";
                }
                return content;
            } catch (Exception e) {
                return "Failed to read document: " + e.getMessage();
            }
        };
    }
}

package com.jobportal.resumeparser.config;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

@Configuration
public class OpenNLPConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenNLPConfig.class);

    /**
     * Load the pre-trained OpenNLP PERSON NER model from classpath
     * (resources/models/en-ner-person.bin) and expose as a Spring Bean.
     */
    @Bean
    public TokenNameFinderModel tokenNameFinderModel() {
        try (InputStream modelIn = new ClassPathResource("models/en-ner-person.bin").getInputStream()) {
            TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
            log.info("✅ OpenNLP PERSON NER model loaded successfully");
            return model;
        } catch (Exception e) {
            log.error("❌ Failed to load OpenNLP en-ner-person.bin model: {}", e.getMessage());
            throw new RuntimeException("Cannot load OpenNLP NER model", e);
        }
    }

    /**
     * Create a NameFinderME bean from the loaded model.
     * NOTE: NameFinderME is NOT thread-safe. In a multi-threaded environment,
     * each parse call should use synchronized access or create a new instance.
     * For simplicity in this application, we use a single bean.
     */
    @Bean
    public NameFinderME nameFinderME(TokenNameFinderModel model) {
        return new NameFinderME(model);
    }
}

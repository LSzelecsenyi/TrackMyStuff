package eu.strictworkout.account;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
class PrivacyPageController {

    private final String english;
    private final String hungarian;

    PrivacyPageController() throws IOException {
        this.english = new ClassPathResource("privacy-en.html").getContentAsString(StandardCharsets.UTF_8);
        this.hungarian = new ClassPathResource("privacy-hu.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping(value = {"/privacy", "/privacy/en"}, produces = MediaType.TEXT_HTML_VALUE)
    String english() {
        return english;
    }

    @GetMapping(value = "/privacy/hu", produces = MediaType.TEXT_HTML_VALUE)
    String hungarian() {
        return hungarian;
    }
}

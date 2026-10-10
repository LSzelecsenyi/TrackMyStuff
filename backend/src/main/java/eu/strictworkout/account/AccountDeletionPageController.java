package eu.strictworkout.account;

import eu.strictworkout.identity.GoogleProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
class AccountDeletionPageController {

    private final GoogleProperties google;
    private final String page;

    AccountDeletionPageController(GoogleProperties google) throws IOException {
        this.google = google;
        this.page = new ClassPathResource("account-deletion.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/account/delete", produces = MediaType.TEXT_HTML_VALUE)
    String page() {
        String clientId = clientId(google.clientId());
        return page.replace("{{CLIENT_ID}}", clientId)
                .replace("{{CONFIGURED}}", clientId.isEmpty() ? "false" : "true");
    }

    static String clientId(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || !trimmed.matches("[A-Za-z0-9._\\-]+")) {
            return "";
        }
        return trimmed;
    }
}

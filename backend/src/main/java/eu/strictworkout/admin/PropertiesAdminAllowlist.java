package eu.strictworkout.admin;

import org.springframework.stereotype.Component;

@Component
class PropertiesAdminAllowlist implements AdminAllowlist {

    private final AdminProperties properties;

    PropertiesAdminAllowlist(AdminProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean allows(String googleSubject) {
        return googleSubject != null && properties.allowedSubjects().contains(googleSubject);
    }
}

package eu.strictworkout.admin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MutableAdminAllowlist implements AdminAllowlist {

    private final Set<String> subjects = ConcurrentHashMap.newKeySet();

    public void replaceWith(String... allowed) {
        subjects.clear();
        for (String subject : allowed) {
            if (subject != null && !subject.isBlank()) {
                subjects.add(subject);
            }
        }
    }

    @Override
    public boolean allows(String googleSubject) {
        return googleSubject != null && subjects.contains(googleSubject);
    }
}

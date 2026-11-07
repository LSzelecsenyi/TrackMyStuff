package eu.strictworkout.billing;

import eu.strictworkout.account.MarkerKeyRing;
import eu.strictworkout.identity.ExternalIdentityRepository;
import eu.strictworkout.identity.IdentityProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class GooglePurchaseAccountTags implements PurchaseAccountTags {

    private final MarkerKeyRing keys;
    private final ExternalIdentityRepository identities;

    GooglePurchaseAccountTags(MarkerKeyRing keys, ExternalIdentityRepository identities) {
        this.keys = keys;
        this.identities = identities;
    }

    @Override
    public boolean enabled() {
        return keys.configured();
    }

    @Override
    public Optional<String> tagFor(UUID userId) {
        if (!keys.configured() || userId == null) {
            return Optional.empty();
        }
        return identities.findByUser_IdAndProvider(userId, IdentityProvider.GOOGLE)
                .map(identity -> keys.purchaseTag(identity.getProviderSubject()));
    }

    @Override
    public boolean matches(UUID userId, String presentedTag) {
        if (!keys.configured() || userId == null || presentedTag == null || presentedTag.isBlank()) {
            return false;
        }
        return identities.findByUser_IdAndProvider(userId, IdentityProvider.GOOGLE)
                .map(identity -> keys.purchaseTagMatches(identity.getProviderSubject(), presentedTag))
                .orElse(false);
    }
}

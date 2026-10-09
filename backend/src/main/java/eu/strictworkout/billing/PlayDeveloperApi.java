package eu.strictworkout.billing;

import java.time.Instant;
import java.util.List;

record PlayLineItem(
        String productId,
        String basePlanId,
        Instant expiry,
        boolean autoRenewing
) {
}

record PlayPurchase(
        String packageName,
        String state,
        String acknowledgementState,
        String orderId,
        List<PlayLineItem> lineItems
) {
}

enum PlayApiFailure {
    NOT_CONFIGURED,
    INVALID_TOKEN,
    UNAVAILABLE
}

final class PlayApiException extends RuntimeException {
    final PlayApiFailure failure;

    PlayApiException(PlayApiFailure failure) {
        super(failure.name());
        this.failure = failure;
    }
}

interface PlayDeveloperApi {
    PlayPurchase fetch(String packageName, String purchaseToken);

    void acknowledge(String packageName, String productId, String purchaseToken);
}

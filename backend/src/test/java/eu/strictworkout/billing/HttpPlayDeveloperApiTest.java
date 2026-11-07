package eu.strictworkout.billing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpPlayDeveloperApiTest {

    @Test
    void subscriptionsv2ParsesTheAccountTagAndLinkedToken() {
        String body = """
                {
                  "subscriptionState": "SUBSCRIPTION_STATE_ACTIVE",
                  "acknowledgementState": "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
                  "latestOrderId": "GPA.1",
                  "linkedPurchaseToken": "previous-token",
                  "externalAccountIdentifiers": {
                    "obfuscatedExternalAccountId": "1.account"
                  },
                  "lineItems": [{
                    "productId": "strict_pro",
                    "expiryTime": "2026-12-01T00:00:00Z",
                    "offerDetails": {"basePlanId": "monthly"},
                    "autoRenewingPlan": {"autoRenewEnabled": true}
                  }]
                }
                """;
        PlayPurchase purchase = HttpPlayDeveloperApi.parse("com.strictworkout.app", new org.json.JSONObject(body));
        assertEquals("ACTIVE", purchase.state());
        assertEquals("1.account", purchase.obfuscatedAccountId());
        assertEquals("previous-token", purchase.linkedPurchaseToken());
        assertEquals("strict_pro", purchase.lineItems().get(0).productId());
    }

    @Test
    void aPurchaseWithoutAnAccountTagStaysLegacy() {
        PlayPurchase purchase = HttpPlayDeveloperApi.parse(
                "com.strictworkout.app",
                new org.json.JSONObject("{\"subscriptionState\":\"SUBSCRIPTION_STATE_EXPIRED\",\"lineItems\":[]}")
        );
        assertNull(purchase.obfuscatedAccountId());
        assertNull(purchase.linkedPurchaseToken());
        assertEquals("EXPIRED", purchase.state());
    }
}

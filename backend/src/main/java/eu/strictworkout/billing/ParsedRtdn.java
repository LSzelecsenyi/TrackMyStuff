package eu.strictworkout.billing;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

record ParsedRtdn(
        String messageId,
        Instant eventTime,
        String packageName,
        String purchaseToken,
        int notificationType,
        boolean subscription
) {
    static ParsedRtdn parse(String body) {
        JSONObject root = new JSONObject(body);
        JSONObject message = root.getJSONObject("message");
        String messageId = message.getString("messageId");
        byte[] decoded = Base64.getDecoder().decode(message.getString("data"));
        JSONObject notification = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
        Instant eventTime = notification.has("eventTimeMillis")
                ? Instant.ofEpochMilli(Long.parseLong(notification.getString("eventTimeMillis")))
                : null;
        String packageName = notification.optString("packageName", "");
        JSONObject subscription = notification.optJSONObject("subscriptionNotification");
        if (subscription == null) {
            return new ParsedRtdn(messageId, eventTime, packageName, "", 0, false);
        }
        return new ParsedRtdn(
                messageId,
                eventTime,
                packageName,
                subscription.optString("purchaseToken", ""),
                subscription.optInt("notificationType"),
                true
        );
    }
}

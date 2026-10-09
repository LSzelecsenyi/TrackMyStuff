package eu.strictworkout.billing;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Google Play Developer API client. Purchase tokens are not written to logs.
 * When no service-account file is configured, every call fails closed.
 */
final class HttpPlayDeveloperApi implements PlayDeveloperApi {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/androidpublisher";

    private final String serviceAccountFile;
    private final HttpClient http;

    HttpPlayDeveloperApi(String serviceAccountFile) {
        this.serviceAccountFile = serviceAccountFile == null ? "" : serviceAccountFile.trim();
        this.http = HttpClient.newHttpClient();
    }

    @Override
    public PlayPurchase fetch(String packageName, String purchaseToken) {
        if (serviceAccountFile.isEmpty()) {
            throw new PlayApiException(PlayApiFailure.NOT_CONFIGURED);
        }
        String url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
                + encode(packageName)
                + "/purchases/subscriptionsv2/tokens/"
                + encode(purchaseToken);
        JSONObject body = get(url);
        return parse(packageName, body);
    }

    @Override
    public void acknowledge(String packageName, String productId, String purchaseToken) {
        if (serviceAccountFile.isEmpty()) {
            throw new PlayApiException(PlayApiFailure.NOT_CONFIGURED);
        }
        String url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
                + encode(packageName)
                + "/purchases/subscriptions/"
                + encode(productId)
                + "/tokens/"
                + encode(purchaseToken)
                + ":acknowledge";
        post(url, "{}");
    }

    private JSONObject get(String url) {
        return call(HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + accessToken()).GET());
    }

    private void post(String url, String json) {
        call(HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + accessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private JSONObject call(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 400) {
                throw new PlayApiException(PlayApiFailure.INVALID_TOKEN);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PlayApiException(PlayApiFailure.UNAVAILABLE);
            }
            if (response.body() == null || response.body().isBlank()) {
                return new JSONObject();
            }
            return new JSONObject(response.body());
        } catch (PlayApiException error) {
            throw error;
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PlayApiException(PlayApiFailure.UNAVAILABLE);
        }
    }

    private String accessToken() {
        try {
            JSONObject account = new JSONObject(Files.readString(Path.of(serviceAccountFile)));
            String assertion = assertion(account.getString("client_email"), account.getString("private_key"));
            String form = "grant_type=" + encode("urn:ietf:params:oauth:grant-type:jwt-bearer")
                    + "&assertion=" + encode(assertion);
            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PlayApiException(PlayApiFailure.UNAVAILABLE);
            }
            return new JSONObject(response.body()).getString("access_token");
        } catch (PlayApiException error) {
            throw error;
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PlayApiException(PlayApiFailure.NOT_CONFIGURED);
        }
    }

    private static String assertion(String email, String privateKeyPem) {
        String header = base64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        long now = Instant.now().getEpochSecond();
        String claims = base64(new JSONObject()
                .put("iss", email)
                .put("scope", SCOPE)
                .put("aud", TOKEN_URL)
                .put("iat", now)
                .put("exp", now + 3600)
                .toString());
        String unsigned = header + "." + claims;
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey(privateKeyPem));
            signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (Exception error) {
            throw new PlayApiException(PlayApiFailure.NOT_CONFIGURED);
        }
    }

    private static PrivateKey privateKey(String pem) throws Exception {
        String cleaned = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    static PlayPurchase parse(String packageName, JSONObject body) {
        String state = body.optString("subscriptionState", "");
        if (state.startsWith("SUBSCRIPTION_STATE_")) {
            state = state.substring("SUBSCRIPTION_STATE_".length());
        }
        String acknowledgement = body.optString("acknowledgementState", "");
        List<PlayLineItem> lines = new ArrayList<>();
        JSONArray items = body.optJSONArray("lineItems");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                Instant expiry = item.has("expiryTime") && !item.isNull("expiryTime")
                        ? Instant.parse(item.getString("expiryTime"))
                        : null;
                boolean renew = false;
                if (item.opt("autoRenewingPlan") instanceof JSONObject plan) {
                    renew = plan.optBoolean("autoRenewEnabled", true);
                }
                String basePlanId = "";
                if (item.opt("offerDetails") instanceof JSONObject offer) {
                    basePlanId = offer.optString("basePlanId", "");
                }
                lines.add(new PlayLineItem(item.optString("productId", ""), basePlanId, expiry, renew));
            }
        }
        return new PlayPurchase(
                packageName,
                state,
                acknowledgement,
                body.optString("latestOrderId", ""),
                lines
        );
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

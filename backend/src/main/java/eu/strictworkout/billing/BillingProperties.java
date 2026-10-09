package eu.strictworkout.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "strict.billing")
public record BillingProperties(
        String packageName,
        String productIds,
        String serviceAccountFile,
        String rtdnAudience,
        String rtdnServiceAccount
) {
    public BillingProperties {
        packageName = packageName == null ? "" : packageName.trim();
        productIds = productIds == null ? "" : productIds.trim();
        serviceAccountFile = serviceAccountFile == null ? "" : serviceAccountFile.trim();
        rtdnAudience = rtdnAudience == null ? "" : rtdnAudience.trim();
        rtdnServiceAccount = rtdnServiceAccount == null ? "" : rtdnServiceAccount.trim();
    }
}

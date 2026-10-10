package eu.strictworkout.account;

import eu.strictworkout.auth.StrictRequests;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AccountDeletionController {

    private final AccountDeletionService deletion;

    AccountDeletionController(AccountDeletionService deletion) {
        this.deletion = deletion;
    }

    @DeleteMapping("/api/v1/account")
    ResponseEntity<Void> deleteAuthenticated(@RequestBody DeleteAccountRequest request) {
        deletion.delete(request.idToken(), request.confirmed(), StrictRequests.current().userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/account/deletion")
    ResponseEntity<Void> deleteFromWeb(@RequestBody DeleteAccountRequest request) {
        deletion.delete(request.idToken(), request.confirmed(), null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/account/deletion-preview")
    AccountDeletionService.Preview preview(@RequestBody DeleteAccountRequest request) {
        return deletion.preview(request.idToken());
    }

    public record DeleteAccountRequest(String idToken, boolean confirmed) {
    }
}

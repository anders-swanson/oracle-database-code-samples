package com.example;

import java.util.Base64;
import java.util.List;

import com.oracle.bmc.vault.model.Base64SecretContentDetails;
import com.oracle.bmc.vault.model.CreateSecretDetails;
import com.oracle.cloud.spring.vault.VaultTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppController {
    private final VaultTemplate vaultTemplate;

    // The value of the Vault secret "mysecret" will be injected into "mySecretValue"
    // by the Spring property source loader.
    @Value("${s1}")
    private String secret1;

    @Value("${s2}")
    private String secret2;

    public AppController(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    @GetMapping("/values")
    public ResponseEntity<?> showValues() {
        return ResponseEntity.ok(List.of(
                secret1, secret2
        ));
    }

    @PostMapping("/secret")
    public ResponseEntity<?> createSecret(@RequestParam String name, @RequestParam String content) {
        Base64SecretContentDetails contentDetails = Base64SecretContentDetails.builder()
                .content(Base64.getEncoder().encodeToString(content.getBytes()))
                .name(name)
                .build();
        CreateSecretDetails createSecretDetails = CreateSecretDetails.builder()
                .secretContent(contentDetails)
                .build();
        vaultTemplate.createSecret(name, createSecretDetails);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}

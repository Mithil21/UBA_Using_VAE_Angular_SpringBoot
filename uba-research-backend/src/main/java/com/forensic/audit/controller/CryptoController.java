package com.forensic.audit.controller;

import com.forensic.audit.crypto.CryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crypto")
@RequiredArgsConstructor
public class CryptoController {

    private final CryptoService cryptoService;

    @GetMapping(value = "/public-key", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getPublicKey() {
        return ResponseEntity.ok(cryptoService.getPublicKeyPem());
    }
}

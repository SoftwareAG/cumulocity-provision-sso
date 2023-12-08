
package com.c8y.sso.controller;

import java.util.List;

import org.springframework.http.MediaType;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.c8y.sso.service.SSOService;

/**
 * This is an example controller. This should be removed for your real project!
 *
 * @author APES
 */
@RestController
@RequestMapping("/sso")
public class SSOController {

    private SSOService ssoService;

    public SSOController(SSOService ssoService) {
        this.ssoService = ssoService;
    }

    @GetMapping(path = "/providerName", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> getAllDeviceNames() {
        List<String> response = ssoService.getProviderName();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

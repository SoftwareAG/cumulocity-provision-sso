
package com.c8y.sso.controller;

import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.c8y.sso.service.SSOService;
import com.cumulocity.microservice.context.ContextService;
import com.cumulocity.microservice.context.credentials.UserCredentials;

/**
 * This is an example controller. This should be removed for your real project!
 *
 * @author APES
 */

@RestController
// @RequestMapping("/sso")
public class SSOController {

    private SSOService ssoService;
    private static final Logger LOG = LoggerFactory.getLogger(SSOController.class);

    @Autowired
    private ContextService<UserCredentials>contextService;
    public SSOController(SSOService ssoService) {
        this.ssoService = ssoService;
    }


    @RequestMapping(value = "/config/{tenant}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getConfigStatus(@PathVariable @NotNull String tenant) {
        try {
            
            String bootstrapTenant = contextService.getContext().getTenant();

            LOG.info("Get configuration status for tenant {}, called from bootstratp tenant {}", tenant, bootstrapTenant);
            String result = "";
            result = ssoService.getConfig(tenant);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @RequestMapping(value = "/config", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> postConfigStatus(@RequestBody Map<String,String> payload) {
        try {
            String tenant = payload.get("tenant");
            String bootstrapTenant = contextService.getContext().getTenant();

            LOG.info("Writing configuration for tenant {}, called from bootstrap tenant {}", tenant, bootstrapTenant);
            String result = "";
            result = ssoService.postConfig(tenant);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @RequestMapping(value = "/config/{tenant}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteConfigStatus(@PathVariable @NotNull String tenant) {
        try {
            
            String bootstrapTenant = contextService.getContext().getTenant();

            LOG.info("Deleting configuration for tenant {}, called from bootstratp tenant {}", tenant, bootstrapTenant);
            String result = "";
            result = ssoService.deleteConfig(tenant);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

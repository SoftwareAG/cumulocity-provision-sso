package com.c8y.sso.service;

import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionAddedEvent;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.sdk.client.RestConnector;
import com.cumulocity.sdk.client.SDKException;
import com.cumulocity.sdk.client.inventory.InventoryApi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;

import c8y.IsDevice;

/**
 * This is an example service. This should be removed for your real project!
 *
 * @author sagIotPower (ck)
 */
@Service
public class SSOService {

    private static final Logger LOG = LoggerFactory.getLogger(SSOService.class);

    private ObjectMapper objectMapper = new ObjectMapper();

    private InventoryApi inventoryApi;

    @Autowired
    private RestConnector restConnector;

    private RestConnector bootstrapRestConnector = null;

    private final MicroserviceSubscriptionsService subscriptions;

    public SSOService(InventoryApi inventoryApi, MicroserviceSubscriptionsService subscriptions) {
        this.inventoryApi = inventoryApi;
        this.subscriptions = subscriptions;
    }

    @EventListener
    public void initialize(MicroserviceSubscriptionAddedEvent event) {
        // Executed for each tenant subscribed
        String tenant = event.getCredentials().getTenant();
        LOG.info("Subscription from tenant {}!", tenant);
        subscriptions.callForTenant(tenant, () -> {

           if ( bootstrapRestConnector == null ) {
                bootstrapRestConnector = this.restConnector.getPlatformParameters().createRestConnector();
           }
           //Response resp = bootstrapRestConnector.get("/tenant/tenants/" + tenant, MediaType.APPLICATION_JSON_TYPE);
           Response resp = bootstrapRestConnector.get("/tenant/currentTenant", MediaType.APPLICATION_JSON_TYPE);
           String output = resp.readEntity(String.class);
           LOG.info("Details for tenant: {}", output);

            String ssoString = "{\n" +
                    "  \"issuer\": \"https://sts.onko.net/dummy/\",\n" +
                    "  \"redirectToPlatform\": \"https://TENANT_ID.eu-latest.onko.com/tenant/oauth\",\n" +
                    "  \"id\": \"fghhfhgf677rf-f794-4bf3-a4b2-32e5c4fgdummyfhfhfhfh69dumm71d6\",\n" +
                    "  \"providerName\": \"Onko WD\",\n" +
                    "  \"logoutRequest\": {},\n" +
                    "  \"visibleOnLoginPage\": true,\n" +
                    "  \"signatureVerificationConfig\": {\n" +
                    "    \"aad\": {\n" +
                    "      \"publicKeyDiscoveryUrl\": \"https://login.onko-online.com/common/discovery/keys\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";
            // JsonNode jsonNode = objectMapper.readTree(ssoString);
            Map<String, Object> map = objectMapper.readValue(ssoString, new TypeReference<Map<String, Object>>() {
            });

            final ManagedObjectRepresentation managedObjectRepresentation = new ManagedObjectRepresentation();
            managedObjectRepresentation.setName("SSO-Provision-" + tenant);
            managedObjectRepresentation.setType("c8y_sso_provioning");
            managedObjectRepresentation.setProperty("sso_config", map);
            managedObjectRepresentation.set(new IsDevice());
            try {
                final ManagedObjectRepresentation response = inventoryApi.create(managedObjectRepresentation);
                LOG.info("Created Provisioning: {}", response.getId());
                return Optional.of(response);
            } catch (SDKException exception) {
                LOG.error("Error occurred while create a new device", exception);
                return null;
            }
        });
    }
}
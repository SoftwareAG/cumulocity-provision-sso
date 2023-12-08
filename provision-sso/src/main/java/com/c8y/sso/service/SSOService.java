package com.c8y.sso.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionAddedEvent;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.rest.representation.tenant.OptionRepresentation;
import com.cumulocity.sdk.client.PagingParam;
import com.cumulocity.sdk.client.Platform;
import com.cumulocity.sdk.client.QueryParam;
import com.cumulocity.sdk.client.RestConnector;
import com.cumulocity.sdk.client.SDKException;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.option.PagedTenantOptionCollectionRepresentation;
import com.cumulocity.sdk.client.option.TenantOptionCollection;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This is an example service. This should be removed for your real project!
 *
 * @author sagIotPower (ck)
 */
@Service
public class SSOService {

    private static final Logger LOG = LoggerFactory.getLogger(SSOService.class);
    private static final String DEFAULT_BOOSTRAP_TENANT = "t14070519";
    private static final String C8Y_BOOTSTRAP_TENANT = "C8Y_BOOTSTRAP_TENANT";

    private ObjectMapper objectMapper = new ObjectMapper();

    private InventoryApi inventoryApi;

    @Autowired
    private Platform platformApi;

    @Autowired
    private RestConnector restConnector;

    private final MicroserviceSubscriptionsService subscriptions;

    public SSOService(InventoryApi inventoryApi, MicroserviceSubscriptionsService subscriptions) {
        this.inventoryApi = inventoryApi;
        this.subscriptions = subscriptions;
    }

    @EventListener
    public void initialize(MicroserviceSubscriptionAddedEvent event) {
        // Executed for each tenant subscribed
        String tenant = event.getCredentials().getTenant();
        LOG.info("Subscription from tenant: {}", tenant);
        String tenantIdFromConnector = this.restConnector.getPlatformParameters().getCumulocityCredentials()
                .getTenantId();
        LOG.info("TenantId from restConnector: {}", tenantIdFromConnector);

        String tenantBootstrap = getEnv(C8Y_BOOTSTRAP_TENANT, DEFAULT_BOOSTRAP_TENANT);
        LOG.info("Bootstrap tenant from environment: {}", tenantBootstrap);

        try {
            if (!tenantBootstrap.equals(tenant)) {
                List<OptionRepresentation> entireOptionsList = new ArrayList<>();
                TenantOptionCollection tenantOptionCollection = platformApi.getTenantOptionApi().getOptions();
                PagedTenantOptionCollectionRepresentation pagedTenantOptions = tenantOptionCollection.get(1,
                        new QueryParam(PagingParam.WITH_TOTAL_PAGES, "true"));
                pagedTenantOptions.allPages().forEach(entireOptionsList::add);
                pagedTenantOptions.forEach(opt -> {
                    LOG.info("Option from tenant: {}", opt.toJSON());
                });

                final RestConnector restConnectorInjected = this.restConnector;
                subscriptions.callForTenant(tenantBootstrap, new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        Response resp = restConnectorInjected.get("/tenant/loginOptions/OAUTH2",
                                MediaType.APPLICATION_JSON_TYPE);
                        String respString = resp.readEntity(String.class);
                        Map<String, Object> respMap = objectMapper.readValue(respString,
                                new TypeReference<Map<String, Object>>() {
                                });
                        Object providerName = respMap.get("providerName");
                        return providerName;
                    }
                });
            } else {
                final RestConnector restConnectorInternal = this.restConnector.getPlatformParameters()
                        .createRestConnector();
                final RestConnector restConnectorInjected = this.restConnector;
                if (restConnectorInternal == null) {
                    LOG.info("Something is wrong with restConnectorInternal, is null!");
                } else {
                    Object providerName = subscriptions.callForTenant(tenantBootstrap, new Callable<Object>() {
                        @Override
                        public Object call() throws Exception {
                            Response resp = restConnectorInternal.get("/tenant/loginOptions/OAUTH2",
                                    MediaType.APPLICATION_JSON_TYPE);
                            String respString = resp.readEntity(String.class);
                            Map<String, Object> respMap = objectMapper.readValue(respString,
                                    new TypeReference<Map<String, Object>>() {
                                    });
                            Object providerName = respMap.get("providerName");
                            return providerName;
                        }
                    });
                    LOG.info("OAUTH2 providerName from tenant: {}", providerName);
                }
            }
        } catch (SDKException e) {
            LOG.error("Error when retrieving option from tenant: {}", tenant);
            e.printStackTrace();
        }
        subscriptions.callForTenant(tenant, () -> {

            RestConnector restConnector = this.restConnector.getPlatformParameters().createRestConnector();
            // Response resp = bootstrapRestConnector.get("/tenant/tenants/" + tenant,
            // MediaType.APPLICATION_JSON_TYPE);
            Response resp = restConnector.get("/tenant/currentTenant", MediaType.APPLICATION_JSON_TYPE);
            // List<Map<String, Object>> json = resp.readEntity(new
            // GenericType<List<Map<String, Object>>>() {});
            // String domainName = (String) json.get(0).get("domainName");

            String respString = resp.readEntity(String.class);
            Map<String, Object> respMap = objectMapper.readValue(respString, new TypeReference<Map<String, Object>>() {
            });
            LOG.info("Details for tenant: {}", respMap.get("domainName"));
            // String response = resp.readEntity(String.class);
            // LOG.info("Details for tenant: {}", output);

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
            // Map<String, Object> map = objectMapper.readValue(ssoString, new
            // TypeReference<Map<String, Object>>() {
            // });

            // final ManagedObjectRepresentation managedObjectRepresentation = new
            // ManagedObjectRepresentation();
            // managedObjectRepresentation.setName("SSO-Provision-" + tenant);
            // managedObjectRepresentation.setType("c8y_sso_provioning");
            // managedObjectRepresentation.setProperty("sso_config", map);
            // managedObjectRepresentation.set(new IsDevice());
            // try {
            // final ManagedObjectRepresentation response =
            // inventoryApi.create(managedObjectRepresentation);
            // LOG.info("Created Provisioning: {}", response.getId());
            // return Optional.of(response);
            // } catch (SDKException exception) {
            // LOG.error("Error occurred while create a new device", exception);
            // return null;
            // }
            return null;
        });
    }

    private String getEnv(String key, String defaultValue) {
        String value = defaultValue;
        try {
            value = HtmlUtils.htmlEscape(System.getenv(key) != null ? System.getenv(key) : defaultValue);
        } catch (SecurityException e) {
            LOG.error(e.getMessage());
        }
        return value;
    }

    public List<String> getProviderName() {
        return null;
    }
}

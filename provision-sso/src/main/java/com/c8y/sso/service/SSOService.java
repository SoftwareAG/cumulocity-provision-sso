package com.c8y.sso.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import c8y.IsDevice;

/**
 * This is an example service. This should be removed for your real project!
 *
 * @author sagIotPower (ck)
 */
@Service
public class SSOService {

    private static final String REDIRECT_TO_PLATFORM = "redirectToPlatform";
    private static final Logger LOG = LoggerFactory.getLogger(SSOService.class);
    private static final String DEFAULT_BOOSTRAP_TENANT = "t14070519";
    private static final String C8Y_BOOTSTRAP_TENANT = "C8Y_BOOTSTRAP_TENANT";
    private static final Object TEMPLATE_ID = "id";

    private ObjectMapper objectMapper = new ObjectMapper();

    private InventoryApi inventoryApi;

    @Autowired
    private Platform platformApi;

    @Autowired
    private RestConnector restConnector;

    @Value("${C8Y.baseURL}")
    private String baseUrl;

    private final MicroserviceSubscriptionsService subscriptions;

    public SSOService(InventoryApi inventoryApi, MicroserviceSubscriptionsService subscriptions) {
        this.inventoryApi = inventoryApi;
        this.subscriptions = subscriptions;
    }

    @EventListener
    public void initialize(MicroserviceSubscriptionAddedEvent event) {
        String tenant = event.getCredentials().getTenant();
        String tenantFromConnector = this.restConnector.getPlatformParameters().getCumulocityCredentials()
                .getTenantId();
        LOG.info("New subscription tenant / tenantFromConnector / bootstrapTenant: {} / {} / {}", tenant,
                tenantFromConnector, getBootstrapTenant());

        try {
            if (!getBootstrapTenant().equals(tenant)) {
                listTenantOptions();
                Map<String, Object> loginOptionsFromBoostrap = getLoginOptionsFromTenant(getBootstrapTenant());
                try {
                    Map<String, Object> loginOptionsFromTenant = getLoginOptionsFromTenant(tenant);
                    if (loginOptionsFromTenant == null) {
                        writeLoginOptionsToTenant(tenant, loginOptionsFromBoostrap);
                    } else {
                        LOG.info("Option loginOptions exist in tenant {} {}. Do nothing!", tenant,
                                loginOptionsFromTenant);
                    }
                } catch (Exception e) {
                    LOG.info("Ready to provision sso in new tenant!");
                    writeLoginOptionsToTenant(tenant, loginOptionsFromBoostrap);
                }
            } else {
                LOG.info("Susbcription from bootstrapTenant. Do nothing!");
            }
        } catch (SDKException e) {
            LOG.error("Error when retrieving option from tenant: {}", tenant);
            e.printStackTrace();
        }
    }

    private String getDomainName() {
        String domainName = null;
        try {
            RestConnector restConnector = this.restConnector.getPlatformParameters().createRestConnector();
            Response resp = restConnector.get("/tenant/currentTenant", MediaType.APPLICATION_JSON_TYPE);
            String respString = resp.readEntity(String.class);
            Map<String, Object> respMap = objectMapper.readValue(respString, new TypeReference<Map<String, Object>>() {
            });
            domainName = respMap.get("domainName").toString();
            LOG.info("DomainName for tenant: {}", domainName);
        } catch (Exception e) {
            LOG.error("Exception finding: domainName for tenant: {} {}", domainName, e.getMessage());
        }
        return domainName;
    }

    private void writeLoginOptionsToTenant(String tenant, Map<String, Object> loginOptions) {
        // update loginOptions
        loginOptions.put(REDIRECT_TO_PLATFORM, "https://" + getDomainName() + "/tenant/oauth");
        loginOptions.remove(TEMPLATE_ID);
        loginOptions.remove("self");

        String loginOptionsString;
        try {
            loginOptionsString = objectMapper.writeValueAsString(loginOptions);
            LOG.info("Ready to write loginOption to new tenant: {}", loginOptionsString);
            subscriptions.callForTenant(tenant,
                    new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            try {
                                LOG.info("Preparing to write provision sso to new tenant.");
                                String postUrl = baseUrl + "/tenant/loginOptions/OAUTH2";
                                HttpRequest postRequest = HttpRequest.newBuilder()
                                        .uri(URI.create((postUrl)))
                                        .headers("Authorization",
                                                subscriptions.getCredentials(subscriptions.getTenant())
                                                        .get().toCumulocityCredentials().getAuthenticationString(),
                                                "Accept", "application/json",
                                                "Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(loginOptionsString))
                                        .build();
                                HttpClient httpClient = HttpClient.newHttpClient();
                                HttpResponse<String> resp = httpClient.send(postRequest,
                                        HttpResponse.BodyHandlers.ofString());
                                LOG.info("Response from writting provision sso to new tenant: {}", resp.toString());
                            } catch (Exception e) {
                                LOG.error("Exception writting provision sso to new tenant: {}", e.getMessage());
                                e.printStackTrace();
                            }
                            return "success";
                        }
                    });
            LOG.info("Wrote provision sso to new tenant: {}", getDomainName());
        } catch (JsonProcessingException e) {
            LOG.error("Serializing LoginOptions failed: {}", e.getMessage());
        }
    }

    private void listTenantOptions() {
        List<OptionRepresentation> entireOptionsList = new ArrayList<>();
        TenantOptionCollection tenantOptionCollection = platformApi.getTenantOptionApi().getOptions();
        PagedTenantOptionCollectionRepresentation pagedTenantOptions = tenantOptionCollection.get(1,
                new QueryParam(PagingParam.WITH_TOTAL_PAGES, "true"));
        pagedTenantOptions.allPages().forEach(entireOptionsList::add);
        pagedTenantOptions.forEach(opt -> {
            LOG.info("Option from tenant: {}", opt.toJSON());
        });
    }

    private String getBootstrapTenant() {
        String bootstrapTenant = getEnv(C8Y_BOOTSTRAP_TENANT, DEFAULT_BOOSTRAP_TENANT);
        return bootstrapTenant;
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

    private Map<String, Object> getLoginOptionsFromTenant(String tenant) {
        final RestConnector restConnectorInjected = this.restConnector;
        Map<String, Object> loginOptionsFromBoostrap = subscriptions.callForTenant(tenant,
                new Callable<Map<String, Object>>() {
                    @Override
                    public Map<String, Object> call() throws Exception {
                        Response resp = restConnectorInjected.get("/tenant/loginOptions/OAUTH2",
                                MediaType.APPLICATION_JSON_TYPE);
                        String respString = resp.readEntity(String.class);
                        Map<String, Object> respMap = objectMapper.readValue(respString,
                                new TypeReference<Map<String, Object>>() {
                                });
                        String templateName = respMap.get("template").toString();
                        LOG.info("Returned loginOptions: {} {}", resp.getStatus(), templateName);
                        if (resp.getStatus() == 404) {
                            LOG.info("No OAuth2 template found!");
                            respMap = null;
                        }
                        return respMap;
                    }
                });
        return loginOptionsFromBoostrap;
    }

    private Optional<ManagedObjectRepresentation> createMO(String tenant, String ssoString)
            throws JsonMappingException, JsonProcessingException {
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
    }
}

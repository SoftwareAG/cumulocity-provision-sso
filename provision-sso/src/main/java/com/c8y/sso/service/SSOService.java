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

import javax.validation.constraints.NotNull;
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
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionRemovedEvent;
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionsInitializedEvent;
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
    private static final Object TEMPLATE_ID = "id";

    private ObjectMapper objectMapper = new ObjectMapper();

    private InventoryApi inventoryApi;

    @Autowired
    private Platform platformApi;

    @Autowired
    private RestConnector restConnector;

    @Value("${C8Y.baseURL}")
    private String baseUrl;

    @Value("${C8Y.bootstrap.tenant}")
    private String bootstrapTenant;

    private final MicroserviceSubscriptionsService subscriptions;

    public SSOService(InventoryApi inventoryApi, MicroserviceSubscriptionsService subscriptions) {
        this.inventoryApi = inventoryApi;
        this.subscriptions = subscriptions;
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
                        LOG.debug("Returned loginOptions: {} {}", resp.getStatus(), templateName);
                        if (resp.getStatus() == 404) {
                            LOG.info("No OAuth2 template found!");
                            respMap = null;
                        }
                        return respMap;
                    }
                });
        return loginOptionsFromBoostrap;
    }

    public String getConfig(@NotNull String tenant) {
        LOG.debug("Ready to get loginOption from tenant {}.", tenant);

        String result = subscriptions.callForTenant(tenant,
                new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        try {
                            LOG.info("Preparing to get loginOptions from tenant.");
                            String getUrl = baseUrl + "/tenant/loginOptions/OAUTH2";
                            HttpRequest getRequest = HttpRequest.newBuilder()
                                    .uri(URI.create((getUrl)))
                                    .headers("Authorization",
                                            subscriptions.getCredentials(subscriptions.getTenant())
                                                    .get().toCumulocityCredentials().getAuthenticationString(),
                                            "Accept", "application/json",
                                            "Content-Type", "application/json")
                                    .GET()
                                    .build();
                            HttpClient httpClient = HttpClient.newHttpClient();
                            HttpResponse<String> resp = httpClient.send(getRequest,
                                HttpResponse.BodyHandlers.ofString()); 
                            LOG.info("Response from getting loginOptions from tenant: {}", resp.body());
                            return resp.body();
                        } catch (Exception e) {
                            LOG.error("Exception deleting provision sso from tenant: {}", e.getMessage());
                            e.printStackTrace();
                            throw (e);
                        }
                    }
                });
        return result;
    }

    public String postConfig(@NotNull String tenant) {
        if (!bootstrapTenant.equals(tenant)) {
            // write loginOptions only if tenant is not Bootstrap tenant
            Map<String, Object> loginOptions = getLoginOptionsFromTenant(bootstrapTenant);

            loginOptions.put(REDIRECT_TO_PLATFORM, "https://" + getDomainName() + "/tenant/oauth");
            loginOptions.remove(TEMPLATE_ID);
            loginOptions.remove("self");

            String loginOptionsString;
            try {
                loginOptionsString = objectMapper.writeValueAsString(loginOptions);
                LOG.debug("Ready to write loginOption to new tenant: {}", loginOptionsString);
                String result = subscriptions.callForTenant(tenant,
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
                                    LOG.info("Response from writing provision sso to new tenant: {}", resp.body());
                                return resp.body().toString();
                                } catch (Exception e) {
                                    LOG.error("Exception writing provision sso to new tenant: {}", e.getMessage());
                                    e.printStackTrace();
                                    throw (e);
                                }
                            }
                        });
                LOG.info("Wrote provision sso to new tenant: {} with domain name {}", tenant, getDomainName());
                return result;
            } catch (JsonProcessingException e) {
                LOG.error("Serializing LoginOptions failed: {}", e.getMessage());
                return "";
            }
        } else {
            return "";
        }
    }

    public String deleteConfig(@NotNull String tenant) {
        if (!bootstrapTenant.equals(tenant)) {
            LOG.debug("Ready to delete loginOption from tenant.");
            String result = subscriptions.callForTenant(tenant,
                    new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            try {
                                LOG.info("Preparing to delete provision sso from tenant.");
                                String deleteUrl = baseUrl + "/tenant/loginOptions/OAUTH2";
                                HttpRequest deleteRequest = HttpRequest.newBuilder()
                                        .uri(URI.create((deleteUrl)))
                                        .headers("Authorization",
                                                subscriptions.getCredentials(subscriptions.getTenant())
                                                        .get().toCumulocityCredentials().getAuthenticationString(),
                                                "Accept", "application/json",
                                                "Content-Type", "application/json")
                                        .DELETE()
                                        .build();
                                HttpClient httpClient = HttpClient.newHttpClient();
                                HttpResponse resp = httpClient.send(deleteRequest,
                                        HttpResponse.BodyHandlers.ofString());
                                LOG.info("Response from deleting provision sso from tenant: {}", resp.toString());
                                return resp.body().toString();
                            } catch (Exception e) {
                                LOG.error("Exception deleting provision sso from tenant: {}", e.getMessage());
                                e.printStackTrace();
                                throw (e);
                            }
                        }
                    });
            LOG.info("Deleted provision sso from tenant: {} with domain name {}", tenant, getDomainName());
            return result;
        } else {
            return "";
        }
    }
}

package com.colak.springsecurityoauth2standaloneclient.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
public class TestConfig {

    private static final String RESOURCE_ENDPOINT = "_resource_endpoint";

    private static final String OAUTH2_TOKEN_ENDPOINT = "_oauth2_token_endpoint";

    // if this block is not added then all urls are redirected to default login /login url
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                //.csrf(AbstractHttpConfigurer::disable)
                //.oauth2Client(withDefaults())
                .build();
    }

    @Bean
    public WebClient webClientTest(final @Value("${oauth2.registration.id}") String oauth2RegistrationId,
                                   final @Value("${resource.base}") String resourceBase,
                                   final ClientRegistrationRepository clientRegistrationRepository) {
        var resourceEndpointLogger = LoggerFactory.getLogger(oauth2RegistrationId + RESOURCE_ENDPOINT);

        var oauth = createOAuth2ClientExchangeFilterFunction(oauth2RegistrationId, clientRegistrationRepository);
        oauth.setDefaultClientRegistrationId(oauth2RegistrationId);

        return WebClient.builder()
                // base path of the client, just path while calling is required
                .baseUrl(resourceBase)
                .apply(oauth.oauth2Configuration())
                .filter(logResourceRequest(resourceEndpointLogger, oauth2RegistrationId))
                .filter(logResourceResponse(resourceEndpointLogger, oauth2RegistrationId))
                .build();
    }

    // WebClient logger : WebClient: For Client test, Sending OAUTH2 protected Resource Request to GET: http://localhost:64203/test
    private static ExchangeFilterFunction logResourceRequest(final Logger logger, final String clientName) {
        return ExchangeFilterFunction.ofRequestProcessor(c -> {
            logger.info(
                    "WebClient : For Client {}, Sending OAUTH2 protected Resource Request to {}: {}",
                    clientName, c.method(), c.url()
            );
            return Mono.just(c);
        });
    }

    // WebClient logger : WebClient: For Client test, OAUTH2 protected Resource Response status: 200 OK
    private static ExchangeFilterFunction logResourceResponse(final Logger logger, final String clientName) {
        return ExchangeFilterFunction.ofResponseProcessor(c -> {
            logger.info("WebClient : For Client {}, OAUTH2 protected Resource Response status: {}", clientName, c.statusCode());
            return Mono.just(c);
        });
    }

    private ServletOAuth2AuthorizedClientExchangeFilterFunction createOAuth2ClientExchangeFilterFunction(
            String oauth2RegistrationId,
            ClientRegistrationRepository clientRegistrationRepository) {
        var tokenEndpointLogger = LoggerFactory.getLogger(oauth2RegistrationId + OAUTH2_TOKEN_ENDPOINT);

        var defaultClientCredentialsTokenResponseClient = new DefaultClientCredentialsTokenResponseClient();
        defaultClientCredentialsTokenResponseClient
                .setRestOperations(getRestTemplateForTokenEndPoint(oauth2RegistrationId, tokenEndpointLogger));

        var provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(c -> c.accessTokenResponseClient(defaultClientCredentialsTokenResponseClient))
                .build();

        var oauth2AuthorizedClientService = new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);

        var authorizedClientServiceOAuth2AuthorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, oauth2AuthorizedClientService);
        authorizedClientServiceOAuth2AuthorizedClientManager.setAuthorizedClientProvider(provider);

        var oauth = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientServiceOAuth2AuthorizedClientManager);
        oauth.setDefaultClientRegistrationId(oauth2RegistrationId);
        return oauth;
    }

    private RestTemplate getRestTemplateForTokenEndPoint(String oauth2RegistrationId, Logger tokenEndpointLogger) {
        var restTemplateForTokenEndPoint = new RestTemplate();
        restTemplateForTokenEndPoint
                .setMessageConverters(
                        List.of(new FormHttpMessageConverter(),
                                new OAuth2AccessTokenResponseHttpMessageConverter()
                        ));
        restTemplateForTokenEndPoint
                .setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        restTemplateForTokenEndPoint
                .setInterceptors(List.of(restTemplateRequestInterceptor(tokenEndpointLogger, oauth2RegistrationId)));
        return restTemplateForTokenEndPoint;
    }

    private static ClientHttpRequestInterceptor restTemplateRequestInterceptor(final Logger logger, final String clientName) {
        return (request, body, execution) -> {
            logger.info("RestTemplate For Client {}, Sending OAUTH2 Token Request to Authentication Server {}", clientName, request.getURI());
            var clientResponse = execution.execute(request, body);
            logger.info("RestTemplate For Client {}, Authentication Server's OAUTH2 Token Response: {}", clientName, clientResponse.getStatusCode());
            return clientResponse;
        };
    }

}

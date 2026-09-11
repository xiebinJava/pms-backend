package com.brad.pms.auth;

import com.brad.pms.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DefaultOidcTokenClient implements OidcTokenClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public OidcEndpoints discover(String issuer) {
        String metadataUrl = trimSlash(issuer) + "/.well-known/openid-configuration";
        JsonNode node = getJson(metadataUrl, null);
        String authorize = text(node, "authorization_endpoint");
        String token = text(node, "token_endpoint");
        if (authorize.isBlank() || token.isBlank()) {
            throw BusinessException.error("SSO 发现文档缺少授权或令牌地址");
        }
        return new OidcEndpoints(authorize, token, text(node, "userinfo_endpoint"));
    }

    @Override
    public OidcTokenResponse exchange(AuthProviderProperties.Oidc oidc, OidcEndpoints endpoints, String code, String codeVerifier) {
        String body = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(oidc.getRedirectUri())
                + "&client_id=" + enc(oidc.getClientId())
                + "&client_secret=" + enc(oidc.getClientSecret())
                + "&code_verifier=" + enc(codeVerifier);
        JsonNode node = postForm(endpoints.tokenUri(), body);
        String accessToken = text(node, "access_token");
        String idToken = text(node, "id_token");
        if (accessToken.isBlank() && idToken.isBlank()) {
            throw BusinessException.unauthorized("SSO 未返回可用令牌");
        }
        return new OidcTokenResponse(accessToken, idToken);
    }

    @Override
    public Map<String, Object> userInfo(String userinfoUri, String accessToken) {
        if (userinfoUri == null || userinfoUri.isBlank() || accessToken == null || accessToken.isBlank()) {
            return Map.of();
        }
        JsonNode node = getJson(userinfoUri, accessToken);
        Map<String, Object> claims = new LinkedHashMap<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode value = node.get(name);
            if (value != null && value.isValueNode()) {
                claims.put(name, value.asText());
            }
        }
        return claims;
    }

    private JsonNode getJson(String url, String bearer) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET();
            if (bearer != null) {
                builder.header("Authorization", "Bearer " + bearer);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw BusinessException.unauthorized("SSO 请求失败");
            }
            return objectMapper.readTree(response.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.unauthorized("无法连接 SSO 服务");
        }
    }

    private JsonNode postForm(String url, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw BusinessException.unauthorized("SSO 授权码无效");
            }
            return objectMapper.readTree(response.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.unauthorized("无法连接 SSO 服务");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String value) {
        if (value == null) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

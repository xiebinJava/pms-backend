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
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigInteger;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

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
        return new OidcEndpoints(authorize, token, text(node, "userinfo_endpoint"),
                text(node, "jwks_uri"), text(node, "issuer"));
    }

    @Override
    public OidcTokenResponse exchange(AuthProviderProperties.Oidc oidc, OidcEndpoints endpoints, String code, String codeVerifier) {
        String body = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(oidc.getRedirectUri())
                + "&client_id=" + enc(oidc.getClientId())
                + "&code_verifier=" + enc(codeVerifier);
        if (oidc.getClientSecret() != null && !oidc.getClientSecret().isBlank()) {
            body += "&client_secret=" + enc(oidc.getClientSecret());
        }
        JsonNode node = postForm(endpoints.tokenUri(), body);
        String accessToken = text(node, "access_token");
        String idToken = text(node, "id_token");
        if (accessToken.isBlank() && idToken.isBlank()) {
            throw BusinessException.unauthorized("SSO 未返回可用令牌");
        }
        return new OidcTokenResponse(accessToken, idToken);
    }

    @Override
    public OidcValidatedClaims validateIdToken(AuthProviderProperties.Oidc oidc,
                                               OidcEndpoints endpoints,
                                               String idToken,
                                               String nonce) {
        if (idToken == null || idToken.isBlank()) {
            throw BusinessException.unauthorized("SSO 未返回 ID Token");
        }
        if (endpoints.jwksUri() == null || endpoints.jwksUri().isBlank()) {
            throw BusinessException.error("SSO 未配置 JWKS 地址，无法校验 ID Token");
        }
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("ID Token 格式无效");
            JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!"RS256".equals(header.path("alg").asText())) {
                throw new IllegalArgumentException("ID Token 仅支持 RS256");
            }
            String kid = header.path("kid").asText("");
            JsonNode jwks = getJson(endpoints.jwksUri(), null);
            JsonNode jwk = null;
            for (JsonNode key : jwks.path("keys")) {
                if (kid.equals(key.path("kid").asText()) && "RSA".equals(key.path("kty").asText())) {
                    jwk = key;
                    break;
                }
            }
            if (jwk == null) throw new IllegalArgumentException("找不到 ID Token 签名密钥");
            PublicKey publicKey = rsaPublicKey(jwk.path("n").asText(), jwk.path("e").asText());
            String issuer = firstNonBlank(endpoints.issuer(), oidc.getIssuer());
            if (issuer.isBlank()) throw new IllegalArgumentException("SSO 发行方为空");
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .requireIssuer(issuer)
                    .requireAudience(oidc.getClientId())
                    .require("nonce", nonce)
                    .build()
                    .parseClaimsJws(idToken)
                    .getBody();
            if (claims.getSubject() == null || claims.getSubject().isBlank()) {
                throw new IllegalArgumentException("ID Token 缺少 subject");
            }
            String email = claims.get("email", String.class);
            Boolean verified = claims.get("email_verified", Boolean.class);
            return new OidcValidatedClaims(issuer, claims.getSubject(), email, verified == null || verified);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.unauthorized("SSO ID Token 校验失败");
        }
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

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second == null ? "" : second;
    }

    private static PublicKey rsaPublicKey(String encodedModulus, String encodedExponent) throws Exception {
        if (encodedModulus.isBlank() || encodedExponent.isBlank()) {
            throw new IllegalArgumentException("RSA JWK 缺少参数");
        }
        byte[] modulus = Base64.getUrlDecoder().decode(encodedModulus);
        byte[] exponent = Base64.getUrlDecoder().decode(encodedExponent);
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                new BigInteger(1, modulus), new BigInteger(1, exponent)));
    }
}

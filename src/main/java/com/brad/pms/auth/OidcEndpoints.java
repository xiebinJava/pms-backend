package com.brad.pms.auth;

public record OidcEndpoints(String authorizationUri, String tokenUri, String userinfoUri) {
}

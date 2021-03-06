package com.r6lab.sparkjava.jwt.controller;

import com.r6lab.sparkjava.jwt.TokenService;
import com.r6lab.sparkjava.jwt.user.UserPrincipal;
import spark.Request;

public abstract class AbstractTokenController {

    private static final String TOKEN_PREFIX = "Bearer";

    private final TokenService tokenService;

    public AbstractTokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public static String getToken(Request request) {
        String authorizationHeader = request.headers("Authorization");
        String tokenCookie = request.cookie("token");
        String token = null;

        if(authorizationHeader != null) {
            token = authorizationHeader.replace(TOKEN_PREFIX, "");
        } else if(tokenCookie != null) {
            token = tokenCookie;
        }
        
        return token;
    }
    
    public UserPrincipal getUserPrincipal(Request request) {
        String token = getToken(request);
        return tokenService.getUserPrincipal(token);
    }

    public String getUserNameFromToken(Request request) {
        String token = getToken(request);
        return tokenService.getUserPrincipal(token).getUserName();
    }

}

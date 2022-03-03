package com.r6lab.sparkjava.jwt.user;

public final class UserPrincipal {
    
    private final String userName;

    private UserPrincipal(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return userName;
    }


    public static UserPrincipal of(String userName) {
        return new UserPrincipal(userName);
    }
}

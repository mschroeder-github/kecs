package com.r6lab.sparkjava.jwt.user;

public final class User {

    private final String userName;
    private final String password;
    
    private final String firstName;
    private final String lastName;

    private User(String username, String passwordHash, String firstName, String lastName) {
        this.userName = username;
        this.password = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public static final User of(String username, String passwordHash, String firstName, String lastName) {
        return new User(username, passwordHash, firstName, lastName);
    }
}

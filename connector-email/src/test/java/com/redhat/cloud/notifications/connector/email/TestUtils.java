package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.model.settings.User;

import java.util.Arrays;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public class TestUtils {

    public static User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "-email");
        return user;
    }

    public static Set<User> createUsers(String... usernames) {
        return Arrays.stream(usernames)
                .map(TestUtils::createUser)
                .collect(toSet());
    }
}

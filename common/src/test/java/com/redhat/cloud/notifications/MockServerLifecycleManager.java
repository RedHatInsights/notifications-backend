package com.redhat.cloud.notifications;

import org.mockserver.client.MockServerClient;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.utility.DockerImageName;

public class MockServerLifecycleManager {

    // Keep the version synced with pom.xml.
    private static final DockerImageName DOCKER_IMAGE = DockerImageName.parse("jamesdbloom/mockserver").withTag("mockserver-5.13.1");

    private static MockServerContainer container;
    private static String containerUrl;
    private static MockServerClient client;

    public static void start() {
        container = new MockServerContainer(DOCKER_IMAGE);
        container.start();
        containerUrl = "http://" + container.getContainerIpAddress() + ":" + container.getServerPort();
        client = new MockServerClient(container.getContainerIpAddress(), container.getServerPort());
    }

    public static String getContainerUrl() {
        return containerUrl;
    }

    public static MockServerClient getClient() {
        return client;
    }

    public static void stop() {
        if (client != null) {
            client.stop();
        }
        if (container != null) {
            container.stop();
        }
    }
}

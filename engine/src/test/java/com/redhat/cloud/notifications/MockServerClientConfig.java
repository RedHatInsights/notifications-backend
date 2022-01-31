package com.redhat.cloud.notifications;

import org.mockserver.client.MockServerClient;

public class MockServerClientConfig {

    private final MockServerClient mockServerClient;

    public MockServerClientConfig(String containerIpAddress, Integer serverPort) {
        mockServerClient = new MockServerClient(containerIpAddress, serverPort);
    }

    public MockServerClient getMockServerClient() {
        return mockServerClient;
    }

    public String getRunningAddress() {
        return String.format("%s:%d", mockServerClient.remoteAddress().getHostName(), mockServerClient.remoteAddress().getPort());
    }
}

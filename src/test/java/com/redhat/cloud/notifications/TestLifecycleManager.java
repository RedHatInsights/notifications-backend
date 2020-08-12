/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.cloud.notifications;


import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author hrupp
 */
public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

  PostgreSQLContainer<?> postgreSQLContainer;
  MockServerContainer mockEngineServer;
  MockServerClient mockServerClient;

  @Override
  public Map<String, String> start() {
    System.err.println("++++  TestLifecycleManager start +++");
    Map<String, String> properties = new HashMap<>();
    try {
      setupPostgres(properties);
    } catch (SQLException throwables) {
      throw new RuntimeException(throwables);
    }
    setupMockEngine(properties);

    System.out.println(" -- Running with properties: " + properties);
    return properties;
  }

  @Override
  public void stop() {
    postgreSQLContainer.stop();
  }


  @Override
  public void inject(Object testInstance) {
    if (testInstance instanceof AbstractITest) {
      AbstractITest test = (AbstractITest) testInstance;
      // TODO put in here what needs injection
    }
  }

  void setupPostgres(Map<String, String> props) throws SQLException {
    postgreSQLContainer =
         new PostgreSQLContainer<>("postgres");
    postgreSQLContainer.start();
    // Now that postgres is started, we need to get its URL and tell Quarkus
    // quarkus.datasource.driver=io.opentracing.contrib.jdbc.TracingDriver
    // Driver needs a 'tracing' in the middle like jdbc:tracing:postgresql://localhost:5432/postgres
    String jdbcUrl = postgreSQLContainer.getJdbcUrl();
    String dbUrl = jdbcUrl.substring(jdbcUrl.indexOf(':') + 1);
    String classicJdbcUrl = "jdbc:" /* + "tracing:" */ + dbUrl;
    props.put("quarkus.datasource.jdbc.url", classicJdbcUrl);
    String vertxJdbcUrl = "vertx-reactive:" + dbUrl;
    props.put("quarkus.datasource.reactive.url", "vertx-reactive:" + vertxJdbcUrl);
    props.put("quarkus.datasource.username", "test");
    props.put("quarkus.datasource.password", "test");
    props.put("quarkus.datasource.db-kind", "postgresql");

    // Install the pgcrypto extension
    // Could perhas be done by a migration with a lower number than the 'live' ones.
    PGSimpleDataSource ds = new PGSimpleDataSource();
    ds.setURL(jdbcUrl);
    Connection connection = ds.getConnection("test", "test");
    Statement statement = connection.createStatement();
    statement.execute("CREATE EXTENSION pgcrypto;");
    statement.close();
    connection.close();

  }

  void setupMockEngine(Map<String, String> props) {
    mockEngineServer = new MockServerContainer();

    // set up mock engine
    mockEngineServer.start();
    String mockServerUrl = "http://" + mockEngineServer.getContainerIpAddress() + ":" + mockEngineServer.getServerPort();
    mockServerClient = new MockServerClient(mockEngineServer.getContainerIpAddress(), mockEngineServer.getServerPort());

    mockRbac();

    props.put("engine/mp-rest/url", mockServerUrl);
    props.put("rbac/mp-rest/url", mockServerUrl);
    props.put("notifications/mp-rest/url", mockServerUrl);

  }

  private void mockRbac() {
    // RBac server
    String fullAccessRbac = AbstractITest.getStringFromFile("rbac_example_full_access.json", false);
    String noAccessRbac = AbstractITest.getStringFromFile("rbac_example_no_access.json", false);
    mockServerClient
        .when(request()
                  .withPath("/api/rbac/v1/access/")
                  .withQueryStringParameter("application", "policies")
                  .withHeader("x-rh-identity", ".*2UtZG9lLXVzZXIifQ==") // normal user all allowed
        )
        .respond(HttpResponse.response()
                     .withStatusCode(200)
                     .withHeader("Content-Type", "application/json")
                     .withBody(fullAccessRbac)

        );
    mockServerClient
        .when(request()
                  .withPath("/api/rbac/v1/access/")
                  .withQueryStringParameter("application", "policies")
                  .withHeader("x-rh-identity", ".*kYW1wZi11c2VyIn0=") // hans dampf user nothing allowed
        )
        .respond(HttpResponse.response()
                     .withStatusCode(200)
                     .withHeader("Content-Type", "application/json")
                     .withBody(noAccessRbac)
        );
  }


}

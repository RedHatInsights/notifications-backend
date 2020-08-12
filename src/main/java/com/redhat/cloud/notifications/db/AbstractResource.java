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
package com.redhat.cloud.notifications.db;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import reactor.core.publisher.Mono;

/**
 * @author hrupp
 */
public class AbstractResource {

  Mono<PostgresqlConnection> getPostgresConnection() {
    Config config = ConfigProvider.getConfig();
    // jdbc:postgresql://192.168.1.139:5432/notifications[?blabla]
    String jdbcUrl = config.getValue("quarkus.datasource.jdbc.url", String.class);
    String user = config.getValue("quarkus.datasource.username", String.class);
    String pass = config.getValue("quarkus.datasource.password", String.class);

    String database = jdbcUrl.substring(jdbcUrl.lastIndexOf("/") + 1);
    if (database.contains("?")) {
        database = database.substring(0, database.indexOf("?"));
    }
    String hostPort = jdbcUrl.substring(jdbcUrl.indexOf("://") + 3, jdbcUrl.lastIndexOf("/"));
    String host;
    int port = 5432;
    if (hostPort.contains(":")) {
        host = hostPort.substring(0, hostPort.indexOf(":"));
        port = Integer.parseInt(hostPort.substring(hostPort.indexOf(":") + 1));
    } else {
        host = hostPort;
    }

    PostgresqlConnectionFactory connectionFactory =
      new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host(host)
            .port(port)
            .username(user)
            .password(pass)
            .database(database)
            .build());

    Mono<PostgresqlConnection> connectionPublisher = connectionFactory.create();

    return connectionPublisher;
  }
}

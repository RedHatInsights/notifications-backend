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

import io.restassured.http.Header;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * @author hrupp
 */
public abstract class AbstractITest {

  static Header authHeader;       // User with access rights
  static Header authRbacNoAccess; // Hans Dampf has no rbac access rights
  static Header authHeaderNoAccount; // Account number is empty

  static final String API_BASE_V1_0 = "/api/notifications/v1.0";
  static final String API_BASE_V1 = "/api/notifications/v1";

  @BeforeAll
  static void setupRhId() {
    // provide rh-id
    String rhid = getStringFromFile("rhid.txt", false);
    authHeader = new Header("x-rh-identity", rhid);
    rhid = getStringFromFile("rhid_hans.txt", false);
    authRbacNoAccess = new Header("x-rh-identity", rhid);
    rhid = getStringFromFile("rhid_no_account.txt", false);

    authHeaderNoAccount = new Header("x-rh-identity", rhid);
  }

  public static String getStringFromFile(String filename, boolean removeTrailingNewline) {
    String rhid = "";
    try (FileInputStream fis = new FileInputStream("src/test/resources/" + filename)) {
      Reader r = new InputStreamReader(fis, StandardCharsets.UTF_8);
      StringBuilder sb = new StringBuilder();
      char[] buf = new char[1024];
      int chars_read = r.read(buf);
      while (chars_read >= 0) {
        sb.append(buf, 0, chars_read);
        chars_read = r.read(buf);
      }
      r.close();
      rhid = sb.toString();
      if (removeTrailingNewline && rhid.endsWith("\n")) {
        rhid = rhid.substring(0, rhid.indexOf('\n'));
      }
    } catch (IOException ioe) {
      Assertions.fail("File reading failed: " + ioe.getMessage());
    }
    return rhid;
  }

}

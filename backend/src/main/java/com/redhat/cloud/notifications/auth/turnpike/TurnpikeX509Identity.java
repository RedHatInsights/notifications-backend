/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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

package com.redhat.cloud.notifications.auth.turnpike;

/**
 * author hrupp
 */
public class TurnpikeX509Identity extends TurnpikeIdentity {
    public X509 x509;

    @Override
    public String getName() {
        return x509.subject_dn;
    }

    public class X509 {
        public String subject_dn;
        public String issuer_dn;
    }
}

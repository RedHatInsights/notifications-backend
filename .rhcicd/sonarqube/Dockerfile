# Image available at https://catalog.redhat.com/software/containers/ubi9/openjdk-17-runtime.
FROM registry.access.redhat.com/ubi9/openjdk-17-runtime

# Switch to the root user to be able to install the required packages.
USER root

# This argument holds the new password for the "cacerts" keystore.
ARG cacerts_keystore_password
# The argument that holds Red Hat IT's custom certificate's location.
ARG rh_it_root_ca_cert_url

# 1. Install the UUID package which is required for the scripts.
# 2. Change the default "cacert" keystore's password to a custom one for more
#    security.
# 3. Import Red Hat IT's custom self signed certificate in the general
#    "cacerts" file.
# 4. Remove the certificate file.
RUN mkdir --parents "/var/cache/yum/metadata" \
    && microdnf install --assumeyes uuid-1.6.2-55.el9.x86_64 \
    && microdnf clean all \
    && readonly rh_it_root_ca_file="$(mktemp)" \
    && curl --output "${rh_it_root_ca_file}" --insecure "${rh_it_root_ca_cert_url}" \
    && keytool \
        -cacerts \
        -new "${cacerts_keystore_password}" \
        -storepasswd \
        -storepass "changeit" \
    && keytool \
         -alias "RH-IT-Root-CA" \
         -cacerts \
         -file "${rh_it_root_ca_file}" \
         -importcert \
         -noprompt \
         -storepass "${cacerts_keystore_password}" \
         -trustcacerts \
    && rm "${rh_it_root_ca_file}"

# Go back to the default, unprivileged user.
USER default

# Copy the repository contents.
COPY --chown=default:default . /home/default

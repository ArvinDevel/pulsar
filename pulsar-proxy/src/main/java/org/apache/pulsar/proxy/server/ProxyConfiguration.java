/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.proxy.server;

import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import org.apache.pulsar.broker.authorization.PulsarAuthorizationProvider;
import org.apache.pulsar.common.configuration.FieldContext;
import org.apache.pulsar.common.configuration.PulsarConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@Setter
public class ProxyConfiguration implements PulsarConfiguration {
    private final static Logger log = LoggerFactory.getLogger(ProxyConfiguration.class);

    // Local-Zookeeper quorum connection string
    private String zookeeperServers;
    @Deprecated
    // Global-Zookeeper quorum connection string
    private String globalZookeeperServers;

    // Configuration Store connection string
    private String configurationStoreServers;

    // ZooKeeper session timeout
    private int zookeeperSessionTimeoutMs = 30_000;

    // if Service Discovery is Disabled this url should point to the discovery service provider.
    private String brokerServiceURL;
    private String brokerServiceURLTLS;

    // These settings are unnecessary if `zookeeperServers` is specified
    private String brokerWebServiceURL;
    private String brokerWebServiceURLTLS;

    // function worker web services
    private String functionWorkerWebServiceURL;
    private String functionWorkerWebServiceURLTLS;

    // Port to use to server binary-proto request
    private int servicePort = 6650;
    // Port to use to server binary-proto-tls request
    private int servicePortTls = 6651;

    // Port to use to server HTTP request
    private int webServicePort = 8080;
    // Port to use to server HTTPS request
    private int webServicePortTls = 8443;

    // Path for the file used to determine the rotation status for the broker
    // when responding to service discovery health checks
    private String statusFilePath;

    // Role names that are treated as "super-user", meaning they will be able to
    // do all admin operations and publish/consume from all topics
    private Set<String> superUserRoles = Sets.newTreeSet();

    // Enable authentication
    private boolean authenticationEnabled = false;
    // Authentication provider name list, which is a list of class names
    private Set<String> authenticationProviders = Sets.newTreeSet();
    // Enforce authorization
    private boolean authorizationEnabled = false;
    // Authorization provider fully qualified class-name
    private String authorizationProvider = PulsarAuthorizationProvider.class.getName();
    // Forward client authData to Broker for re authorization
    // make sure authentication is enabled for this to take effect
    private boolean forwardAuthorizationCredentials = false;

    // Max concurrent inbound Connections
    private int maxConcurrentInboundConnections = 10000;

    // Max concurrent outbound Connections
    private int maxConcurrentLookupRequests = 50000;

    // Authentication settings of the proxy itself. Used to connect to brokers
    private String brokerClientAuthenticationPlugin;
    private String brokerClientAuthenticationParameters;
    private String brokerClientTrustCertsFilePath;

    /***** --- TLS --- ****/
    // Enable TLS for the proxy handler
    private boolean tlsEnabledInProxy = false;

    // Enable TLS when talking with the brokers
    private boolean tlsEnabledWithBroker = false;

    // Path for the TLS certificate file
    private String tlsCertificateFilePath;
    // Path for the TLS private key file
    private String tlsKeyFilePath;
    // Path for the trusted TLS certificate file
    private String tlsTrustCertsFilePath;
    // Accept untrusted TLS certificate from client
    private boolean tlsAllowInsecureConnection = false;
    // Validates hostname when proxy creates tls connection with broker
    private boolean tlsHostnameVerificationEnabled = false;
    // Specify the tls protocols the broker will use to negotiate during TLS Handshake.
    // Example:- [TLSv1.2, TLSv1.1, TLSv1]
    private Set<String> tlsProtocols = Sets.newTreeSet();
    // Specify the tls cipher the broker will use to negotiate during TLS Handshake.
    // Example:- [TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256]
    private Set<String> tlsCiphers = Sets.newTreeSet();
    // Specify whether Client certificates are required for TLS
    // Reject the Connection if the Client Certificate is not trusted.
    private boolean tlsRequireTrustedClientCertOnConnect = false;

    // Http redirects to redirect to non-pulsar services
    private Set<HttpReverseProxyConfig> httpReverseProxyConfigs = Sets.newHashSet();

    // Http output buffer size. The amount of data that will be buffered for http requests
    // before it is flushed to the channel. A larger buffer size may result in higher http throughput
    // though it may take longer for the client to see data.
    // If using HTTP streaming via the reverse proxy, this should be set to the minimum value, 1,
    // so that clients see the data as soon as possible.
    @FieldContext(minValue = 1)
    private int httpOutputBufferSize = 32*1024;

    private Properties properties = new Properties();

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;

        Map<String, Map<String, String>> redirects = new HashMap<>();
        Pattern redirectPattern = Pattern.compile("^httpReverseProxy\\.([^\\.]*)\\.(.+)$");
        Map<String, List<Matcher>> groups = properties.stringPropertyNames().stream()
            .map((s) -> redirectPattern.matcher(s))
            .filter(Matcher::matches)
            .collect(Collectors.groupingBy((m) -> m.group(1))); // group by name

        groups.entrySet().forEach((e) -> {
                Map<String, String> keyToFullKey = e.getValue().stream().collect(
                        Collectors.toMap(m -> m.group(2), m -> m.group(0)));
                if (!keyToFullKey.containsKey("path")) {
                    throw new IllegalArgumentException(
                            String.format("httpReverseProxy.%s.path must be specified exactly once", e.getKey()));
                }
                if (!keyToFullKey.containsKey("proxyTo")) {
                    throw new IllegalArgumentException(
                            String.format("httpReverseProxy.%s.proxyTo must be specified exactly once", e.getKey()));
                }
                httpReverseProxyConfigs.add(new HttpReverseProxyConfig(e.getKey(),
                                                    properties.getProperty(keyToFullKey.get("path")),
                                                    properties.getProperty(keyToFullKey.get("proxyTo"))));
            });
    }

    public static class HttpReverseProxyConfig {
        private final String name;
        private final String path;
        private final String proxyTo;

        HttpReverseProxyConfig(String name, String path, String proxyTo) {
            this.name = name;
            this.path = path;
            this.proxyTo = proxyTo;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public String getProxyTo() {
            return proxyTo;
        }

        @Override
        public String toString() {
            return String.format("HttpReverseProxyConfig(%s, path=%s, proxyTo=%s)", name, path, proxyTo);
        }
    }
}

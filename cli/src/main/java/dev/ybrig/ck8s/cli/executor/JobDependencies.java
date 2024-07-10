package dev.ybrig.ck8s.cli.executor;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.runtime.v2.runner.el.DefaultExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.common.Mapper;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.dependencymanager.DependencyManager.MAVEN_SCHEME;

public final class JobDependencies {

    private static final Logger log = LoggerFactory.getLogger(JobDependencies.class);

    public static List<String> get(Ck8sPayload payload, List<String> originalDependencies) {
        Collection<URI> uris = getDependencyUris(originalDependencies);
        if (uris.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, String> versions = getDependencyVersions(payload);
        if (versions.isEmpty()) {
            return originalDependencies;
        }

        return updateVersions(uris, versions).stream()
                .map(URI::toString)
                .collect(Collectors.toList());
    }

    private static Collection<URI> updateVersions(Collection<URI> uris, Map<String, String> versions) {
        List<URI> result = new ArrayList<>();
        for (URI item : uris) {
            String scheme = item.getScheme();
            if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
                IdAndVersion idv = IdAndVersion.parse(item.getAuthority());
                if (isLatestVersion(idv.version)) {
                    String version = versions.get(idv.id);
                    if (version != null) {
                        item = URI.create(MAVEN_SCHEME + "://" + idv.id + ":" + assertVersion(idv.id, versions));
                    } else {
                        LogUtils.info("Can't determine the version of {}, using as-is...", item);
                    }
                }
            }

            result.add(item);
        }

        return result;
    }

    private static Collection<URI> getDependencyUris(Collection<String> deps) {
        try {
            return normalizeUrls(deps);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Error while reading the list of dependencies: " + e.getMessage(), e);
        }
    }

    private static Collection<URI> normalizeUrls(Collection<String> urls) throws IOException, URISyntaxException {
        if (urls == null || urls.isEmpty()) {
            return Collections.emptySet();
        }

        Collection<URI> result = new HashSet<>();

        for (String s : urls) {
            URI u = new URI(s);
            String scheme = u.getScheme();

            if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
                result.add(u);
                continue;
            }

            if (scheme == null || scheme.trim().isEmpty()) {
                throw new IOException("Invalid dependency URL. Missing URL scheme: " + s);
            }

            if (s.endsWith(".jar")) {
                result.add(u);
                continue;
            }

            URL url = u.toURL();
            while (true) {
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    URLConnection conn = url.openConnection();
                    if (conn instanceof HttpURLConnection) {
                        HttpURLConnection httpConn = (HttpURLConnection) conn;
                        httpConn.setInstanceFollowRedirects(false);

                        int code = httpConn.getResponseCode();
                        if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                                code == HttpURLConnection.HTTP_MOVED_PERM ||
                                code == HttpURLConnection.HTTP_SEE_OTHER ||
                                code == 307) {

                            String location = httpConn.getHeaderField("Location");
                            url = new URL(location);
                            log.info("normalizeUrls -> using: {}", location);

                            continue;
                        }

                        u = url.toURI();
                    } else {
                        log.warn("normalizeUrls -> unexpected connection type: {} (for {})", conn.getClass(), s);
                    }
                }

                break;
            }

            result.add(u);
        }

        return result;
    }

    private static Map<String, String> getDependencyVersions(Ck8sPayload payload) {
        Path componentsLocation = payload.ck8sFlows().location().resolve("ck8s-components");

        Map<String, String> result = new HashMap<>();
        Path ck8sVersions = componentsLocation.resolve("concord/dependency-versions-policy.yaml");
        if (Files.exists(ck8sVersions)) {
            try {
                result.putAll(parseDependenciesVersions(Mapper.yamlMapper().readMap(ck8sVersions)));
            } catch (Exception e) {
                throw new RuntimeException("Error while reading the list of dependencies from '" + ck8sVersions + "': " + e.getMessage());
            }
        }

        Path ck8sExtVersions = componentsLocation.resolve("concord/policy/dependency-versions-policy.yaml");
        if (Files.exists(ck8sExtVersions)) {
            try {
                result.putAll(parseDependenciesVersions(Mapper.yamlMapper().readMap(ck8sExtVersions)));
            } catch (Exception e) {
                throw new RuntimeException("Error while reading the list of dependencies from '" + ck8sExtVersions + "': " + e.getMessage());
            }
        }

        DefaultExpressionEvaluator expressionEvaluator = new DefaultExpressionEvaluator(new TaskProviders());
        EvalContext ctx = EvalContext.builder().variables(new MapBackedVariables(payload.arguments())).build();
        Map<String, String> interpolated = new HashMap<>();
        for (Map.Entry<String, String> e : result.entrySet()) {
            interpolated.put(e.getKey(), expressionEvaluator.eval(ctx, e.getValue(), String.class));
        }

        return interpolated;
    }

    private static Map<String, String> parseDependenciesVersions(Map<String, Object> versionsMap) {
        List<Map<String, Object>> ck8sVersionsList = MapUtils.getList(versionsMap, "dependencyVersions");
        if (ck8sVersionsList.isEmpty()) {
            throw new RuntimeException("'dependencyVersions' element not found");
        }

        Map<String, String> result = new HashMap<>();
        for (Map<String, Object> e : ck8sVersionsList) {
            result.put(MapUtils.assertString(e, "artifact"), MapUtils.assertString(e, "version"));
        }
        return result;
    }

    private static boolean isLatestVersion(String v) {
        return v.equalsIgnoreCase("latest");
    }

    private static String assertVersion(String dep, Map<String, String> versions) {
        String version = versions.get(dep);
        if (version != null) {
            return version;
        }
        throw new IllegalArgumentException("Unofficial dependency '" + dep + "': version is required");
    }

    private static class IdAndVersion {

        public static IdAndVersion parse(String s) {
            int i = s.lastIndexOf(':');
            if (i >= 0 && i + 1 < s.length()) {
                String id = s.substring(0, i);
                String v = s.substring(i + 1);
                return new IdAndVersion(id, v);
            }

            throw new IllegalArgumentException("Invalid artifact ID format: " + s);
        }

        private final String id;
        private final String version;

        private IdAndVersion(String id, String version) {
            this.id = id;
            this.version = version;
        }
    }

    private JobDependencies() {
    }
}

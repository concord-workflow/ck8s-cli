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

import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.sdk.Constants;
import dev.ybrig.ck8s.cli.common.Ck8sPath;
import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JobDependencies {

    public static Set<String> get(Ck8sPath ck8s, ProcessDefinition processDefinition, Map<String, Object> overlayCfg, List<String> activeProfiles) {
        var overlayDeps = prepareDependencies(processDefinition, overlayCfg, activeProfiles);
        var cliDeps = prepareCliDeps(ck8s);

        return Stream.concat(overlayDeps.stream(), cliDeps.stream()).collect(Collectors.toSet());
    }

    private JobDependencies() {
    }

    private static List<String> prepareDependencies(ProcessDefinition processDefinition, Map<String, Object> overlayCfg, List<String> activeProfiles) {
        var deps = new ArrayList<String>(MapUtils.getList(overlayCfg, Constants.Request.DEPENDENCIES_KEY, List.of()));

        // "extraDependencies" are additive: ALL extra dependencies from ALL ACTIVE profiles are added to the list
        var extraDeps = activeProfiles.stream()
                .flatMap(profileName -> Stream.ofNullable(processDefinition.profiles().get(profileName)))
                .flatMap(profile -> profile.configuration().extraDependencies().stream())
                .toList();
        deps.addAll(extraDeps);

        return deps;
    }

    private static List<String> prepareCliDeps(Ck8sPath ck8s) {
        var depsFile = ck8s.ck8sDir().resolve("ck8s-cli-dependencies");
        if (Files.notExists(depsFile)) {
            return List.of();
        }

        try (Stream<String> lines = Files.lines(depsFile)) {
            return lines.filter(s -> !s.isBlank())
                    .map(String::trim)
                    .toList();
        } catch (IOException e) {
            LogUtils.error("Error while reading ck8s-cli-dependencies file: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}

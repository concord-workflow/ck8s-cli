package dev.ybrig.ck8s.cli.utils;

import com.walmartlabs.concord.runtime.model.EffectiveConfiguration;
import com.walmartlabs.concord.runtime.model.FlowDefinition;
import com.walmartlabs.concord.runtime.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.model.Profile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replacement for the flow-lookup half of {@code com.walmartlabs.concord.runtime.model.ProcessDefinitionUtils},
 * removed upstream. The overlay-config half is now covered by {@link EffectiveConfiguration#getEffectiveConfiguration}.
 */
public final class ProcessDefinitionUtils {

    public static FlowDefinition getFlow(ProcessDefinition pd, Collection<String> activeProfiles, String flowName) {
        Map<String, FlowDefinition> flows = overlay(pd.flows(), pd.profiles(), activeProfiles, Profile::flows);
        return flows.get(flowName);
    }

    public static Map<String, Object> getProfilesOverlayCfg(ProcessDefinition pd, Collection<String> activeProfiles) {
        return EffectiveConfiguration.getEffectiveConfiguration(pd, activeProfiles);
    }

    private static <T> Map<String, T> overlay(Map<String, ? extends T> base,
                                                Map<String, ? extends Profile> profiles,
                                                Collection<String> activeProfiles,
                                                java.util.function.Function<Profile, Map<String, ? extends T>> extractor) {
        Map<String, T> result = new LinkedHashMap<>(base != null ? base : java.util.Collections.<String, T>emptyMap());
        if (activeProfiles == null || profiles == null) {
            return result;
        }
        for (var profileName : activeProfiles) {
            var profile = profiles.get(profileName);
            if (profile == null) {
                continue;
            }
            var overlayValues = extractor.apply(profile);
            if (overlayValues != null) {
                result.putAll(overlayValues);
            }
        }
        return result;
    }

    private ProcessDefinitionUtils() {
    }
}

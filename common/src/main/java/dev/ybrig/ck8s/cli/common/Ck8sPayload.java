package dev.ybrig.ck8s.cli.common;

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface Ck8sPayload {

    Ck8sPath ck8sPath();

    Ck8sFlows flows();

    /**
     * Concord process additional arguments
     */
    @Value.Default
    default Map<String, Object> args()
    {
        return Collections.emptyMap();
    }

    @Value.Default
    default Concord concord() {
        return Concord.builder().build();
    }

    static ImmutableCk8sPayload.Builder builder()
    {
        return ImmutableCk8sPayload.builder();
    }

    @Value.Immutable
    interface Concord {

        @Nullable
        String org();

        @Nullable
        String project();

        @Value.Default
        default List<String> activeProfiles() {
            return Collections.emptyList();
        }

        @Value.Default
        default Map<String, Object> meta() {
            return Collections.emptyMap();
        }

        static ImmutableConcord.Builder builder() {
            return ImmutableConcord.builder();
        }
    }
}

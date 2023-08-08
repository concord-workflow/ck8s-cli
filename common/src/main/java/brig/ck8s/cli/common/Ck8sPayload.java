package brig.ck8s.cli.common;

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface Ck8sPayload {

//    @Nullable
//    default String flowName() {
//        return MapUtils.getString(args(), "flow");
//    }

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

    static Builder builder()
    {
        return new Builder();
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

    class Builder
            extends ImmutableCk8sPayload.Builder
    {

//        public Builder flow(String name)
//        {
//            if (name != null) {
//                return putArgs("flow", name);
//            }
//            return this;
//        }
    }
}

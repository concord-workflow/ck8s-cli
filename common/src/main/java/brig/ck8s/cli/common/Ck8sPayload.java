package brig.ck8s.cli.common;

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface Ck8sPayload
{

    static Builder builder()
    {
        return new Builder();
    }

    Path location();

    @Value.Default
    default Map<String, Object> concord()
    {
        return Collections.emptyMap();
    }

    default Path flowsPath() {
        return location().resolve("concord");
    }

    default Path rootConcordYaml() {
        return location().resolve("concord.yml");
    }

    String clusterAlias();

    @Nullable
    default String flowName() {
        return MapUtils.getString(args(), "flow");
    }

    @Value.Default
    default Map<String, Object> args()
    {
        return Collections.emptyMap();
    }

    @Value.Default
    default Map<String, Object> meta()
    {
        return Collections.emptyMap();
    }

    @Nullable
    Ck8sPath cks8sPath();

    class Builder
            extends ImmutableCk8sPayload.Builder
    {

        public Builder flow(String name)
        {
            if (name != null) {
                return putArgs("flow", name);
            }
            return this;
        }
    }
}

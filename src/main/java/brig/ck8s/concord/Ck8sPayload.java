package brig.ck8s.concord;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

@Value.Immutable
@RegisterForReflection(ignoreNested = false)
public interface Ck8sPayload {

    Path location();

    @Nullable
    String entryPoint();

    @Value.Default
    default Map<String, String> args() {
        return Collections.emptyMap();
    }

    class Builder extends ImmutableCk8sPayload.Builder {

        public Ck8sPayload.Builder flow(String name) {
            if (name == null) {
                return entryPoint("show")
                        .putArgs("flow", "show");
            } else {
                return entryPoint("normalFlow")
                        .putArgs("flow", name);
            }
        }
    }

    static Builder builder() {
        return new Builder();
    }
}

package brig.ck8s.concord;

import org.immutables.value.Value;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface Ck8sPayload {

    Path location();

    String entryPoint();

    @Value.Default
    default Map<String, Object> args() {
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

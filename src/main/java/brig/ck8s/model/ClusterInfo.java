package brig.ck8s.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown=true)
@JsonSerialize(as = ImmutableClusterInfo.class)
@JsonDeserialize(as = ImmutableClusterInfo.class)
@RegisterForReflection(ignoreNested = false)
public interface ClusterInfo {

    @JsonProperty("clusterName")
    String name();

    String alias();

    String region();

    @Nullable
    String server();

    static ImmutableClusterInfo.Builder builder() {
        return ImmutableClusterInfo.builder();
    }
}

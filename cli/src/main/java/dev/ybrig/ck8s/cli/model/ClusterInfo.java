package dev.ybrig.ck8s.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(as = ImmutableClusterInfo.class)
@JsonDeserialize(as = ImmutableClusterInfo.class)
public interface ClusterInfo {

    @JsonProperty("clusterName")
    String name();

    String alias();

    String region();

    @Nullable
    String server();
}

package dev.ybrig.ck8s.cli.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.List;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"targetDir", "ck8sDir", "ck8sExtDir", "concord", "oidc"})
@JsonSerialize(as = ImmutableCliConfiguration.class)
@JsonDeserialize(as = ImmutableCliConfiguration.class)
public interface CliConfiguration
{

    static ImmutableCliConfiguration.Builder builder()
    {
        return ImmutableCliConfiguration.builder();
    }

    @Nullable
    String targetDir();

    @Nullable
    String ck8sDir();

    @Nullable
    String ck8sExtDir();

    @Value.Default
    @JsonProperty("concord")
    default List<ConcordProfile> concordProfiles()
    {
        return Collections.emptyList();
    }

    @Nullable
    Oidc oidc();

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableOidc.class)
    @JsonDeserialize(as = ImmutableOidc.class)
    interface Oidc
    {

        @Nullable
        String clientId();

        @Nullable
        String oauthUrl();
    }
}

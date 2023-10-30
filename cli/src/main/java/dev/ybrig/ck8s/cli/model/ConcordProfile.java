package dev.ybrig.ck8s.cli.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"alias", "baseUrl", "apiKey"})
@JsonSerialize(as = ImmutableConcordProfile.class)
@JsonDeserialize(as = ImmutableConcordProfile.class)
public interface ConcordProfile
{

    static ImmutableConcordProfile.Builder builder()
    {
        return ImmutableConcordProfile.builder();
    }

    String alias();

    String baseUrl();

    @Nullable
    String apiKey();

    @Nullable
    String defaultOrg();

    @Nullable
    String defaultProject();

    @Value.Default
    default boolean projectPerCluster() {
        return true;
    }
}

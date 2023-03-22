package brig.ck8s.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableConcordConfiguration.class)
@JsonDeserialize(as = ImmutableConcordConfiguration.class)
public interface ConcordConfiguration {

    String baseUrl();

    String apiKey();
}

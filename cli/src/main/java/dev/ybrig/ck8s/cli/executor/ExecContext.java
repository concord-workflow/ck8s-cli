package dev.ybrig.ck8s.cli.executor;

import com.walmartlabs.concord.cli.Verbosity;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface ExecContext {

    Ck8sPayload payload();

    Verbosity verbosity();

    @Nullable
    String profile();

    @Value.Default
    default boolean testMode() {
        return false;
    }

    static ImmutableExecContext.Builder builder() {
        return ImmutableExecContext.builder();
    }
}

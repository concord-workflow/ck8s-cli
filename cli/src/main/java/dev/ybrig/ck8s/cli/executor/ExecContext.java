package dev.ybrig.ck8s.cli.executor;

import com.walmartlabs.concord.cli.Verbosity;
import org.immutables.value.Value;

import javax.annotation.Nullable;

import static dev.ybrig.ck8s.cli.CliApp.SecretsProvider;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface ExecContext {

    Verbosity verbosity();

    @Nullable
    String profile();

    @Value.Default
    default boolean testMode() {
        return false;
    }

    @Nullable
    SecretsProvider secretsProvider();

    @Nullable
    String clientClusterAlias();



    static ImmutableExecContext.Builder builder() {
        return ImmutableExecContext.builder();
    }
}

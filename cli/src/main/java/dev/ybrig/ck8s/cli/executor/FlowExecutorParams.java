package dev.ybrig.ck8s.cli.executor;

import com.walmartlabs.concord.cli.Verbosity;
import dev.ybrig.ck8s.cli.CliApp;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface FlowExecutorParams {

    static ImmutableFlowExecutorParams.Builder builder() {
        return ImmutableFlowExecutorParams.builder();
    }

    ExecutorType executorType();

    String concordProfile();

    String clusterAlias();

    @Nullable
    CliApp.SecretsProvider secretProvider();

    Verbosity verbosity();

    @Value.Default
    default boolean useLocalDependencies() {
        return false;
    }

    @Value.Default
    default long connectTimeout() {
        return 60;
    }

    @Value.Default
    default long responseTimeout() {
        return 60;
    }

    @Nullable
    Path eventsPath();

    @Value.Default
    default boolean isDryRunMode() {
        return false;
    }
}

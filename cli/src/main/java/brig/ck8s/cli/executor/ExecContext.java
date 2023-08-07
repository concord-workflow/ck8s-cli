package brig.ck8s.cli.executor;

import brig.ck8s.cli.common.Ck8sFlows;
import brig.ck8s.cli.common.Ck8sPath;
import com.walmartlabs.concord.cli.Verbosity;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Map;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface ExecContext {

    Ck8sPath ck8sPath();

    Ck8sFlows flows();

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

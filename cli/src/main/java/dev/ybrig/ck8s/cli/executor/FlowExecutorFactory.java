package dev.ybrig.ck8s.cli.executor;

import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.model.ConcordProfile;

public class FlowExecutorFactory {

    public FlowExecutor create(FlowExecutorParams params) {
        ConcordProfile profile = CliConfigurationProvider.getConcordProfile(params.concordProfile());

        ExecutorType type = params.executorType();
        switch (type) {
            case REMOTE: {
                return (payload, flowName, profiles) -> {
                    RemoteFlowExecutor delegate = new RemoteFlowExecutor(profile.baseUrl(), profile.apiKey(), params.connectTimeout(), params.responseTimeout());
                    delegate.execute(params.clusterAlias(), payload, flowName, params.activeProfiles());
                    return 0;
                };
            }
            case CONCORD_CLI: {
                String secretsProvider = null;
                if (params.secretProvider() != null) {
                    secretsProvider = params.secretProvider().name();
                }
                return new ConcordCliFlowExecutor(params.verbosity(), secretsProvider, params.useLocalDependencies(), profile);
            }
            default: {
                throw new IllegalArgumentException("Unknown type: " + type);
            }
        }
    }
}

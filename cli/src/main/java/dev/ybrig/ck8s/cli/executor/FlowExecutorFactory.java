package dev.ybrig.ck8s.cli.executor;

import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.model.ConcordProfile;
import dev.ybrig.ck8s.cli.op.CliOperationContext;

public class FlowExecutorFactory {

    public FlowExecutor create(CliOperationContext ctx) {
        ExecutorType type = ctx.cliApp().getFlowExecutorType().getType();
        switch (type) {
            case REMOTE: {
                ConcordProfile profile = CliConfigurationProvider.getConcordProfile(ctx.cliApp().getProfile());
                return (payload, flowName, profiles) -> {
                    RemoteFlowExecutor delegate = new RemoteFlowExecutor(profile.baseUrl(), profile.apiKey());
                    delegate.execute(ctx.cliApp().getClusterAlias(), payload, flowName, ctx.cliApp().getActiveProfiles());
                    return 0;
                };
            }
            case CONCORD_CLI: {
                String secretsProvider = null;
                if (ctx.cliApp().getSecretsProvider() != null) {
                    secretsProvider = ctx.cliApp().getSecretsProvider().name();
                }
                return new ConcordCliFlowExecutor(ctx.verbosity(), secretsProvider);
            }
            default: {
                throw new IllegalArgumentException("Unknown type: " + type);
            }
        }
    }
}

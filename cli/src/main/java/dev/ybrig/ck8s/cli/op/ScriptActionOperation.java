package dev.ybrig.ck8s.cli.op;

import dev.ybrig.ck8s.cli.CliApp;
import dev.ybrig.ck8s.cli.actions.ActionType;
import dev.ybrig.ck8s.cli.actions.AwsKubeconfigAction;
import dev.ybrig.ck8s.cli.actions.ExecuteScriptAction;
import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.common.Ck8sPath;
import dev.ybrig.ck8s.cli.model.ConcordProfile;

import java.util.HashMap;
import java.util.Map;

public class ScriptActionOperation
        implements CliOperation {
    public Integer execute(CliOperationContext cliOperationContext) {
        var cliApp = cliOperationContext.cliApp();
        var ck8s = cliOperationContext.ck8sPath();
        var actionType = cliApp.getActionType();

        var scriptAction = new ExecuteScriptAction(ck8s);
        switch (actionType) {
            case UP: {
                return scriptAction.perform(cliOperationContext, "ck8sUp");
            }
            case DOWN: {
                return scriptAction.perform(cliOperationContext, "ck8sDown");
            }
            case DNSMASQ_SETUP: {
                return scriptAction.perform(cliOperationContext, "dnsmasqSetup");
            }
            case DNSMASQ_RESTART: {
                return scriptAction.perform(cliOperationContext, "dnsmasqRestart");
            }
            case DOCKER_REGISTRY: {
                return scriptAction.perform(cliOperationContext, "ck8sDockerRegistry");
            }
            case INSTALL_CONCORD: {
                return scriptAction.perform(cliOperationContext, "installConcord");
            }
            case REINSTALL_CONCORD_AGENT_POOL: {
                return scriptAction.perform(cliOperationContext, "reinstallConcordAgentPool");
            }
            case CONSOLE: {
                var concordCfg = CliConfigurationProvider.getConcordProfile(cliApp.getProfile());
                Map<String, String> params = new HashMap<>();
                params.put("CONCORD_URL", concordCfg.baseUrl());
                params.put("CONCORD_ADMIN_TOKEN", concordCfg.apiKey());
                return scriptAction.perform(cliOperationContext, "ck8sConsole", params);
            }
            case AWS_KUBE_CONFIG: {
                return new AwsKubeconfigAction(ck8s, scriptAction).perform(cliOperationContext);
            }
            default: {
                throw new IllegalArgumentException("Unknown action type: " + actionType);
            }
        }
    }
}

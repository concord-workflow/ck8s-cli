package brig.ck8s.cli.op;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.actions.ActionType;
import brig.ck8s.cli.actions.AwsKubeconfigAction;
import brig.ck8s.cli.actions.ExecuteScriptAction;
import brig.ck8s.cli.common.Ck8sRepos;
import brig.ck8s.cli.model.ConcordProfile;

import java.util.HashMap;
import java.util.Map;

import static brig.ck8s.cli.cfg.CliConfigurationProvider.getConcordProfile;

public class ScriptActionOperation
        implements CliOperation
{
    public Integer execute(CliOperationContext cliOperationContext)
    {
        CliApp cliApp = cliOperationContext.cliApp();
        Ck8sRepos ck8s = cliOperationContext.ck8sPath();
        ActionType actionType = cliApp.getActionType();

        ExecuteScriptAction scriptAction = new ExecuteScriptAction(ck8s);
        switch (actionType) {
            case UP -> {
                return scriptAction.perform(cliOperationContext, "ck8sUp");
            }
            case DOWN -> {
                return scriptAction.perform(cliOperationContext, "ck8sDown");
            }
            case DNSMASQ_SETUP -> {
                return scriptAction.perform(cliOperationContext, "dnsmasqSetup");
            }
            case DNSMASQ_RESTART -> {
                return scriptAction.perform(cliOperationContext, "dnsmasqRestart");
            }
            case DOCKER_REGISTRY -> {
                return scriptAction.perform(cliOperationContext, "ck8sDockerRegistry");
            }
            case INSTALL_CONCORD -> {
                return scriptAction.perform(cliOperationContext, "installConcord");
            }
            case REINSTALL_CONCORD_AGENT_POOL -> {
                return scriptAction.perform(cliOperationContext, "reinstallConcordAgentPool");
            }
            case CONSOLE -> {
                ConcordProfile concordCfg = getConcordProfile(cliApp.getProfile());
                Map<String, String> params = new HashMap<>();
                params.put("CONCORD_URL", concordCfg.baseUrl());
                params.put("CONCORD_ADMIN_TOKEN", concordCfg.apiKey());
                return scriptAction.perform(cliOperationContext, "ck8sConsole", params);
            }
            case AWS_KUBE_CONFIG -> {
                return new AwsKubeconfigAction(ck8s, scriptAction).perform(cliOperationContext);
            }
            default -> throw new IllegalArgumentException("Unknown action type: " + actionType);
        }
    }
}

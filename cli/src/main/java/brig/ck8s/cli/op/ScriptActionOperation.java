package brig.ck8s.cli.op;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.actions.ActionType;
import brig.ck8s.cli.actions.AwsKubeconfigAction;
import brig.ck8s.cli.actions.ExecuteScriptAction;
import brig.ck8s.cli.common.Ck8sPath;
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
        Ck8sPath ck8s = cliOperationContext.ck8sPath();
        ActionType actionType = cliApp.getActionType();

        ExecuteScriptAction scriptAction = new ExecuteScriptAction(ck8s);
        switch (actionType) {
            case UP -> {
                return scriptAction.perform("ck8sUp");
            }
            case DOWN -> {
                return scriptAction.perform("ck8sDown");
            }
            case DNSMASQ_SETUP -> {
                return scriptAction.perform("dnsmasqSetup");
            }
            case DNSMASQ_RESTART -> {
                return scriptAction.perform("dnsmasqRestart");
            }
            case DOCKER_REGISTRY -> {
                return scriptAction.perform("ck8sDockerRegistry");
            }
            case INSTALL_CONCORD -> {
                return scriptAction.perform("installConcord");
            }
            case REINSTALL_CONCORD_AGENT_POOL -> {
                return scriptAction.perform("reinstallConcordAgentPool");
            }
            case CONSOLE -> {
                ConcordProfile concordCfg = getConcordProfile(cliApp.getProfile());
                Map<String, String> params = new HashMap<>();
                params.put("CONCORD_URL", concordCfg.baseUrl());
                params.put("CONCORD_ADMIN_TOKEN", concordCfg.apiKey());
                return scriptAction.perform("ck8sConsole", params);
            }
            case AWS_KUBE_CONFIG -> {
                return new AwsKubeconfigAction(ck8s, scriptAction).perform();
            }
            default -> throw new IllegalArgumentException("Unknown action type: " + actionType);
        }
    }
}

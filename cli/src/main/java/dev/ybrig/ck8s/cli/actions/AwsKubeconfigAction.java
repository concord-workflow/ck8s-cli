package dev.ybrig.ck8s.cli.actions;

import dev.ybrig.ck8s.cli.common.Ck8sPath;
import dev.ybrig.ck8s.cli.model.ClusterInfo;
import dev.ybrig.ck8s.cli.op.CliOperationContext;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static dev.ybrig.ck8s.cli.op.ClusterListOperation.getClusterList;

public class AwsKubeconfigAction {

    private final Ck8sPath ck8sPath;
    private final ExecuteScriptAction scriptAction;

    public AwsKubeconfigAction(Ck8sPath ck8sPath, ExecuteScriptAction scriptAction) {
        this.ck8sPath = ck8sPath;
        this.scriptAction = scriptAction;
    }

    public int perform(CliOperationContext cliOperationContext) {
        var kubeHome = Paths.get(System.getProperty("user.home")).resolve(".kube");
        if (Files.isDirectory(kubeHome)) {
            Arrays.stream(Objects.requireNonNull(kubeHome.toFile()
                            .listFiles((f, p) -> p.startsWith("ck8s-config-") && !p.equals("ck8s-config-local"))))
                    .forEach(File::delete);
        }

        var awsKubeconfigScriptFunction = "awsKubeconfig";

        var clusters = getClusterList(ck8sPath);
        for (var e : clusters.entrySet()) {
            if (cliOperationContext.cliApp().getClusterAlias() != null && !e.getValue().alias().equals(cliOperationContext.cliApp().getClusterAlias())) {
                continue;
            }

            var clusterYaml = e.getKey();
            var account = ck8sPath.accountCfgForCluster(clusterYaml).getParent().getFileName().toString();
            if ("local".equals(account)) {
                continue;
            }

            var ci = e.getValue();
            Map<String, String> params = new HashMap<>();
            params.put("NAME", ci.name());
            params.put("REGION", ci.region());
            params.put("ACCOUNT", account);
            params.put("ALIAS", ci.alias());

            LogUtils.info("Creating AWS Kubeconfig for cluster: {}, region: {}, account: {}, alias: {}", ci.name(), ci.region(), account, ci.alias());

            scriptAction.perform(cliOperationContext, awsKubeconfigScriptFunction, params);
        }

        return 0;
    }
}

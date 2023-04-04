package brig.ck8s.actions;

import brig.ck8s.model.ClusterInfo;
import brig.ck8s.utils.Ck8sPath;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AwsKubeconfigAction {

    private final Ck8sPath ck8sPath;
    private final ExecuteScriptAction scriptAction;

    public AwsKubeconfigAction(Ck8sPath ck8sPath, ExecuteScriptAction scriptAction) {
        this.ck8sPath = ck8sPath;
        this.scriptAction = scriptAction;
    }

    public int perform() {
        Path kubeHome = Paths.get(System.getProperty("user.home")).resolve(".kube");
        if (Files.isDirectory(kubeHome)) {
            Arrays.stream(Objects.requireNonNull(kubeHome.toFile().listFiles((f, p) -> p.startsWith("ck8s-config-")))).forEach(File::delete);
        }

        Map<Path, ClusterInfo> clusters =  new ClusterListAction(ck8sPath).getInfo();
        for (Map.Entry<Path, ClusterInfo> e : clusters.entrySet()) {
            Path clusterYaml = e.getKey();
            String account = ck8sPath.accountCfgForCluster(clusterYaml).getParent().getFileName().toString();
            if ("local".equals(account)) {
                continue;
            }

            ClusterInfo ci = e.getValue();
            Map<String, String> params = new HashMap<>();
            params.put("NAME", ci.name());
            params.put("REGION", ci.region());
            params.put("ACCOUNT", account);
            params.put("ALIAS", ci.alias());

            scriptAction.perform("awsKubeconfig", params);
        }

        return 0;
    }
}
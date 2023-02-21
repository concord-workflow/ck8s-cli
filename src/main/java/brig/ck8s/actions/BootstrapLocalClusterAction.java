package brig.ck8s.actions;

import java.nio.file.Path;

public class BootstrapLocalClusterAction {

    private static final String kindName = "ck8s-local";

    private final Path kindKubeconfig = Path.of(System.getProperty("user.home")).resolve(".kube").resolve("ck8s-config-local");

    public int perform() {
        System.out.println("!!!NOT IMPLEMENTED!!!");
        return -1;
    }
}

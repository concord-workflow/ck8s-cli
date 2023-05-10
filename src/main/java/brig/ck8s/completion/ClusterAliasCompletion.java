package brig.ck8s.completion;

import brig.ck8s.actions.ClusterListAction;
import brig.ck8s.cfg.CliConfigurationProvider;
import brig.ck8s.model.ClusterInfo;
import brig.ck8s.utils.Ck8sPath;
import brig.ck8s.utils.LogUtils;

import java.util.Collections;
import java.util.Iterator;

public class ClusterAliasCompletion implements Iterable<String> {

    @Override
    public Iterator<String> iterator() {
        String ck8sDir = CliConfigurationProvider.get().ck8sDir();
        String ck8sExtDir = CliConfigurationProvider.get().ck8sExtDir();
        if (ck8sDir == null) {
            LogUtils.warn("Can't generate cluster aliases autocomplete. No ck8s/ck8sExt dir definition in ck8s-cli configuration.");
            return Collections.emptyIterator();
        }

        return new ClusterListAction(Ck8sPath.from(ck8sDir, ck8sExtDir)).getInfo().values().stream()
                .map(ClusterInfo::alias).iterator();
    }
}

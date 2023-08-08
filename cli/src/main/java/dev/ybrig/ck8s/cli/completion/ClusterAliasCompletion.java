package dev.ybrig.ck8s.cli.completion;

import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.model.ClusterInfo;
import dev.ybrig.ck8s.cli.op.ClusterListOperation;
import dev.ybrig.ck8s.cli.common.Ck8sPath;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import java.util.Collections;
import java.util.Iterator;

public class ClusterAliasCompletion
        implements Iterable<String>
{

    @Override
    public Iterator<String> iterator()
    {
        String ck8sDir = CliConfigurationProvider.get().ck8sDir();
        String ck8sExtDir = CliConfigurationProvider.get().ck8sExtDir();
        if (ck8sDir == null) {
            LogUtils.warn("Can't generate cluster aliases autocomplete. No ck8s/ck8sExt dir definition in ck8s-cli configuration.");
            return Collections.emptyIterator();
        }

        return ClusterListOperation.getClusterList(Ck8sPath.from(ck8sDir, ck8sExtDir))
                .values()
                .stream()
                .map(ClusterInfo::alias)
                .iterator();
    }
}

package brig.ck8s.completion;

import brig.ck8s.cfg.CliConfigurationProvider;
import brig.ck8s.utils.Ck8sPath;
import brig.ck8s.utils.Ck8sUtils;
import brig.ck8s.utils.LogUtils;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

public class FlowCompletion
        implements Iterable<String>
{

    private final ProjectLoaderV2 projectLoader;

    public FlowCompletion()
    {
        this.projectLoader = new ProjectLoaderV2(new NoopImportManager());
    }

    public static void main(String[] args)
    {
        new FlowCompletion().iterator();
    }

    @Override
    public Iterator<String> iterator()
    {
        String ck8sDir = CliConfigurationProvider.get().ck8sDir();
        String ck8sExtDir = CliConfigurationProvider.get().ck8sExtDir();
        if (ck8sDir == null) {
            LogUtils.warn("Can't generate flow name autocomplete. No ck8s/ck8sExt dir definition in ck8s-cli configuration.");
            return Collections.emptyIterator();
        }

        return Ck8sUtils.streamConcordYaml(Ck8sPath.from(ck8sDir, ck8sExtDir))
                .flatMap(p -> flowNames(p).stream())
                .collect(Collectors.toSet()).iterator();
    }

    private Set<String> flowNames(Path concordYaml)
    {
        try {
            ProcessDefinition processDefinition = projectLoader.loadFromFile(concordYaml).getProjectDefinition();
            return processDefinition.flows().keySet();
        }
        catch (Exception e) {
            throw new RuntimeException("Can't parse concord yaml '" + concordYaml + "': " + e.getMessage());
        }
    }
}

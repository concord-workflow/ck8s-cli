package brig.ck8s.cli.completion;

import brig.ck8s.cli.cfg.CliConfigurationProvider;
import brig.ck8s.cli.model.ConcordProfile;

import java.util.Iterator;

public class ProfilesCompletion
        implements Iterable<String>
{

    @Override
    public Iterator<String> iterator()
    {
        return CliConfigurationProvider.get().concordProfiles().stream().map(ConcordProfile::alias).iterator();
    }
}

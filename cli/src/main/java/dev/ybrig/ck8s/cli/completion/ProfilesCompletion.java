package dev.ybrig.ck8s.cli.completion;

import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.model.ConcordProfile;

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

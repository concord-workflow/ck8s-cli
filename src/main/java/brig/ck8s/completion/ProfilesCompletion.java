package brig.ck8s.completion;

import brig.ck8s.cfg.CliConfigurationProvider;
import brig.ck8s.model.ConcordProfile;

import java.util.Iterator;

public class ProfilesCompletion implements Iterable<String> {

    @Override
    public Iterator<String> iterator() {
        return CliConfigurationProvider.get().concordProfiles().stream().map(ConcordProfile::alias).iterator();
    }
}

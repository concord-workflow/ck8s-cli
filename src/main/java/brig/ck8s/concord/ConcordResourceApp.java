package brig.ck8s.concord;

import jakarta.ws.rs.core.Application;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ServerProperties;

import java.util.*;

public class ConcordResourceApp
        extends Application
{

    private final Set<Class<?>> classes;

    public ConcordResourceApp()
    {
        Set<Class<?>> resources = new HashSet<>();
        resources.add(InventoryResource.class);
        resources.add(JacksonFeature.class);

        this.classes = Collections.unmodifiableSet(resources);
    }

    @Override
    public Map<String, Object> getProperties()
    {
        Map<String, Object> m = new HashMap<>();
        m.put(ServerProperties.WADL_FEATURE_DISABLE, "true");
        return m;
    }

    @Override
    public Set<Class<?>> getClasses()
    {
        return classes;
    }
}

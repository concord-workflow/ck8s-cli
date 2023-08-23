package dev.ybrig.ck8s.cli.concord.plugin;

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class Ck8sTaskParams
{
    private final Variables variables;

    public Ck8sTaskParams(Variables variables)
    {
        this.variables = variables;
    }

    public static Ck8sTaskParams of(Variables input, Variables defaults)
    {
        Map<String, Object> variablesMap = new HashMap<>(defaults != null ? defaults.toMap() : Collections.emptyMap());
        variablesMap.putAll(input.toMap());
        return new Ck8sTaskParams(new MapBackedVariables(variablesMap));
    }

    public String ck8sRepoUrl()
    {
        return variables.assertString("ck8sUrl");
    }

    public String ck8sToken() {
        return variables.assertString("ck8sToken");
    }

    public String ck8sRepoRef()
    {
        return variables.assertString("ck8sRef");
    }

    public String ck8sExtRepoUrl()
    {
        return variables.assertString("ck8sExtUrl");
    }

    public String ck8sExtToken() {
        return variables.assertString("ck8sExtToken");
    }

    public String ck8sExtRepoRef()
    {
        return variables.assertString("ck8sExtRef");
    }

    public String clusterAlias()
    {
        return variables.assertString("clusterAlias");
    }

    public boolean includeTests()
    {
        return variables.getBoolean("includeTests", true);
    }

    public Map<String, Object> arguments()
    {
        return variables.getMap("arguments", Collections.emptyMap());
    }

    public Map<String, Object> meta() {
        return variables.getMap("meta", Collections.emptyMap());
    }

    public String project() {
        return variables.getString("project");
    }

    public String flow()
    {
        return variables.assertString("flow");
    }

    public boolean suspend()
    {
        return variables.getBoolean("suspend", true);
    }
}

package dev.ybrig.ck8s.cli.concord;

import com.walmartlabs.concord.client2.CreateInventoryResponse;
import com.walmartlabs.concord.client2.InventoryEntry;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.UUID;

@Path("/api/v1/org")
public class InventoryResource
{

    @POST
    @Path("/{orgName}/inventory")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CreateInventoryResponse createOrUpdate(@PathParam("orgName") String orgName,
            InventoryEntry entry)
    {

        LogUtils.info("createOrUpdate: {}", entry);

        return new CreateInventoryResponse()
                .ok(true)
                .id(new UUID(0, 0))
                .result(CreateInventoryResponse.ResultEnum.CREATED);
    }

    @POST
    @Path("/{orgName}/inventory/{inventoryName}/data/{itemPath:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Object data(@PathParam("orgName") String orgName,
            @PathParam("inventoryName") String inventoryName,
            @PathParam("itemPath") String itemPath,
            Object data)
    {

        LogUtils.info("data ['{}', '{}', '{}', '{}']", orgName, inventoryName, itemPath, data);
        return data;
    }

    @GET
    @Path("/{orgName}/inventory/{inventoryName}/data/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Object get(@PathParam("orgName") String orgName,
            @PathParam("inventoryName") String inventoryName,
            @PathParam("itemPath") String itemPath,
            @QueryParam("singleItem") @DefaultValue("false") boolean singleItem)
    {

        LogUtils.info("get ['{}', '{}', '{}'] -> null", orgName, inventoryName, itemPath);
        return null;
    }
}

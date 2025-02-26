package dev.ybrig.ck8s.cli.concord;

import com.walmartlabs.concord.client2.GenericOperationResult;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/org")
public class JsonStoreDataResource {

    @PUT
    @Path("/{orgName}/jsonstore/{storeName}/item/{itemPath:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult data(@PathParam("orgName") String orgName,
                                       @PathParam("storeName") String storeName,
                                       @PathParam("itemPath") String itemPath,
                                       Object data) {

        LogUtils.info("json store data ['{}', '{}', '{}', '{}']", orgName, storeName, itemPath, data);
        return new GenericOperationResult()
                .ok(true)
                .result(GenericOperationResult.ResultEnum.CREATED);
    }
}

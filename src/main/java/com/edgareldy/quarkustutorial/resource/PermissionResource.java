package com.edgareldy.quarkustutorial.resource;

import com.edgareldy.quarkustutorial.dto.common.ApiResponse;
import com.edgareldy.quarkustutorial.dto.rbac.PermissionRequest;
import com.edgareldy.quarkustutorial.dto.rbac.PermissionResponse;
import com.edgareldy.quarkustutorial.service.RbacService;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.resteasy.reactive.ResponseStatus;

/**
 * Permission catalog administration (no endpoint here manages which roles hold a permission).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@Path("/api/v1/permissions")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "jwt")
public class PermissionResource {

    @Inject
    RbacService rbacService;

    @GET
    @PermissionsAllowed("PERMISSION:READ")
    public ApiResponse<List<PermissionResponse>> list() {
        return ApiResponse.success(rbacService.listPermissions(), "Permissions");
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("PERMISSION:WRITE")
    @ResponseStatus(201)
    public ApiResponse<PermissionResponse> create(@Valid PermissionRequest request) {
        return ApiResponse.success(rbacService.createPermission(request), "Permission created");
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("PERMISSION:WRITE")
    public ApiResponse<PermissionResponse> update(@PathParam("id") Long id, @Valid PermissionRequest request) {
        return ApiResponse.success(rbacService.updatePermission(id, request), "Permission updated");
    }

    @DELETE
    @Path("/{id}")
    @PermissionsAllowed("PERMISSION:WRITE")
    public ApiResponse<Void> delete(@PathParam("id") Long id) {
        rbacService.deletePermission(id);
        return ApiResponse.success(null, "Permission deleted");
    }
}

package com.edgareldy.quarkustutorial.resource;

import com.edgareldy.quarkustutorial.dto.common.ApiResponse;
import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.CategoryRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.CategoryResponse;
import com.edgareldy.quarkustutorial.service.CategoryService;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.resteasy.reactive.ResponseStatus;

/**
 * CRUD endpoints for product categories.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@Path("/api/v1/categories")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "jwt")
public class CategoryResource {

    @Inject
    CategoryService categoryService;

    @GET
    @PermissionsAllowed("CATEGORY:READ")
    public ApiResponse<PageResponse<CategoryResponse>> list(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(categoryService.list(page, size), "Categories");
    }

    @GET
    @Path("/{id}")
    @PermissionsAllowed("CATEGORY:READ")
    public ApiResponse<CategoryResponse> get(@PathParam("id") Long id) {
        return ApiResponse.success(categoryService.findById(id), "Category");
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("CATEGORY:WRITE")
    @ResponseStatus(201)
    public ApiResponse<CategoryResponse> create(@Valid CategoryRequest request) {
        return ApiResponse.success(categoryService.create(request), "Category created");
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("CATEGORY:WRITE")
    public ApiResponse<CategoryResponse> update(@PathParam("id") Long id, @Valid CategoryRequest request) {
        return ApiResponse.success(categoryService.update(id, request), "Category updated");
    }

    @DELETE
    @Path("/{id}")
    @PermissionsAllowed("CATEGORY:WRITE")
    public ApiResponse<Void> delete(@PathParam("id") Long id) {
        categoryService.delete(id);
        return ApiResponse.success(null, "Category deleted");
    }
}

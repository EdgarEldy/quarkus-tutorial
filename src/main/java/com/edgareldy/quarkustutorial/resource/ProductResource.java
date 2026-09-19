package com.edgareldy.quarkustutorial.resource;

import com.edgareldy.quarkustutorial.dto.common.ApiResponse;
import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.ProductRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.ProductResponse;
import com.edgareldy.quarkustutorial.service.ProductService;
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
 * CRUD endpoints for products, with an optional category filter on the list.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@Path("/api/v1/products")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "jwt")
public class ProductResource {

    @Inject
    ProductService productService;

    @GET
    @PermissionsAllowed("PRODUCT:READ")
    public ApiResponse<PageResponse<ProductResponse>> list(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size,
            @QueryParam("categoryId") Long categoryId) {
        return ApiResponse.success(productService.list(page, size, categoryId), "Products");
    }

    @GET
    @Path("/{id}")
    @PermissionsAllowed("PRODUCT:READ")
    public ApiResponse<ProductResponse> get(@PathParam("id") Long id) {
        return ApiResponse.success(productService.findById(id), "Product");
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("PRODUCT:WRITE")
    @ResponseStatus(201)
    public ApiResponse<ProductResponse> create(@Valid ProductRequest request) {
        return ApiResponse.success(productService.create(request), "Product created");
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("PRODUCT:WRITE")
    public ApiResponse<ProductResponse> update(@PathParam("id") Long id, @Valid ProductRequest request) {
        return ApiResponse.success(productService.update(id, request), "Product updated");
    }

    @DELETE
    @Path("/{id}")
    @PermissionsAllowed("PRODUCT:WRITE")
    public ApiResponse<Void> delete(@PathParam("id") Long id) {
        productService.delete(id);
        return ApiResponse.success(null, "Product deleted");
    }
}

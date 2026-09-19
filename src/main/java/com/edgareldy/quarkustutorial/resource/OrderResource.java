package com.edgareldy.quarkustutorial.resource;

import com.edgareldy.quarkustutorial.dto.common.ApiResponse;
import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.OrderRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.OrderResponse;
import com.edgareldy.quarkustutorial.service.OrderService;
import com.edgareldy.quarkustutorial.service.impl.OrderEventBroadcaster;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * Endpoints to list, read and place orders, with optional customer and product filters on the list.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@Path("/api/v1/orders")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "jwt")
public class OrderResource {

    @Inject
    OrderService orderService;

    @GET
    @PermissionsAllowed("ORDER:READ")
    public ApiResponse<PageResponse<OrderResponse>> list(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size,
            @QueryParam("customerId") Long customerId,
            @QueryParam("productId") Long productId) {
        return ApiResponse.success(orderService.list(page, size, customerId, productId), "Orders");
    }

    @Inject
    OrderEventBroadcaster broadcaster;

    // Server-Sent Events: one long-lived HTTP response, one event per new order. Returning a Mutiny
    // Multi is enough, Quarkus REST subscribes, honours backpressure and writes each item as it arrives
    // (no manual thread handling). The ApiResponse envelope does not apply to a stream of events, each
    // element is a bare OrderResponse. The literal segment "stream" wins over the "/{id}" template.
    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    // @Blocking only concerns the request pipeline: the JWT blacklist filter runs a JDBC query, which
    // is forbidden on the event loop where a Multi endpoint would otherwise be dispatched. The stream
    // itself stays fully reactive.
    @Blocking
    @PermissionsAllowed("ORDER:READ")
    public Multi<OrderResponse> stream() {
        return broadcaster.stream();
    }

    @GET
    @Path("/{id}")
    @PermissionsAllowed("ORDER:READ")
    public ApiResponse<OrderResponse> get(@PathParam("id") Long id) {
        return ApiResponse.success(orderService.findById(id), "Order");
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("ORDER:WRITE")
    @ResponseStatus(201)
    public ApiResponse<OrderResponse> create(@Valid OrderRequest request) {
        return ApiResponse.success(orderService.create(request), "Order created");
    }
}

package com.edgareldy.quarkustutorial.exception;

import com.edgareldy.quarkustutorial.dto.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

/**
 * Test-only resource throwing the exceptions whose Problem Details rendering is verified. Lives in src/test only.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@Path("/test-support")
public class ThrowingTestResource {

    /**
     * Request body with a mandatory field, used to trigger Bean Validation.
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : quarkus-tutorial
     */
    public record Payload(@NotBlank String name) {
    }

    @GET
    @Path("/not-found")
    public String notFound() {
        throw new ResourceNotFoundException("Thing 42 does not exist");
    }

    @GET
    @Path("/business-rule")
    public String businessRule() {
        throw new BusinessRuleException("Rule X forbids this");
    }

    @GET
    @Path("/unexpected")
    public String unexpected() {
        throw new IllegalStateException("secret internal detail");
    }

    @POST
    @Path("/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    @jakarta.ws.rs.Produces(MediaType.APPLICATION_JSON)
    public ApiResponse<String> validate(@Valid Payload payload) {
        return ApiResponse.success(payload.name(), "ok");
    }
}

package com.github.kgoedert;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

public interface AnkiService {
    @POST
    @Path("/")
    @Produces("application/json")
    Response post(String json);
}
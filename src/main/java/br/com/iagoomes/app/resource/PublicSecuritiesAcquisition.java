package br.com.iagoomes.app.resource;

import br.com.iagoomes.api.PublicSecuritiesApi;
import br.com.iagoomes.model.PublicSecuritiesAcquisitionRequest;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PublicSecuritiesAcquisition implements PublicSecuritiesApi {
    @Override
    public Response createPublicSecuritiesAcquisition(PublicSecuritiesAcquisitionRequest publicSecuritiesAcquisitionRequest) {
        log.info(publicSecuritiesAcquisitionRequest.toString());
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @Override
    public Response getCustomerPublicSecurities(String customerId) {
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }
}

package com.ashtonit.odb.realm;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;


/**
 * @author Bruce Ashton
 */
public class TestResponseHandler implements ResponseHandler<String> {

    /**
     * @see ResponseHandler#handleResponse(HttpResponse)
     */
    @Override
    public String handleResponse(final HttpResponse response) throws IOException {
        final StringBuilder builder = new StringBuilder(response.getStatusLine().toString());
        builder.append(System.getProperty("line.separator"));
        final HttpEntity entity = response.getEntity();
        builder.append(EntityUtils.toString(entity));
        return builder.toString();
    }
}

package poc.apigateway.pylon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class RequestBase {
    private final List<Pair> pathParams = new ArrayList<>();
    private final List<Pair> queryParams = new ArrayList<>();
    private final Map<String, String> headerParams = new HashMap<>();
    private Object body;

    public List<Pair> getPathParams() {
        return pathParams;
    }

    public List<Pair> getQueryParams() {
        return queryParams;
    }

    public Map<String, String> getHeaderParams() {
        return headerParams;
    }

    public Object getBody() {
        return body;
    }

    protected void addPathParam(String name, Object value) {
        pathParams.add(new Pair(name, String.valueOf(value)));
    }

    protected void addQueryParam(String name, Object value) {
        if (value != null) {
            queryParams.add(new Pair(name, String.valueOf(value)));
        }
    }

    protected void addHeaderParam(String name, String value) {
        headerParams.put(name, value);
    }

    protected void setBody(Object body) {
        this.body = body;
    }
}

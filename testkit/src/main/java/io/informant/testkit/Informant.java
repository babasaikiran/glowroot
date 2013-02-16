/**
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.informant.testkit;

import io.informant.core.util.ThreadSafe;
import io.informant.testkit.LogMessage.Level;
import io.informant.testkit.Trace.ExceptionInfo;
import io.informant.testkit.internal.GsonFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import checkers.igj.quals.ReadOnly;
import checkers.nullness.quals.Nullable;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.Response;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
// even though this is thread safe, it is not useful for running tests in parallel since
// getLastTrace() and others are not scoped to a particular test
@ThreadSafe
public class Informant {

    private static final Gson gson = GsonFactory.create();

    private final int uiPort;
    private final AsyncHttpClient asyncHttpClient;

    Informant(int uiPort, AsyncHttpClient asyncHttpClient) {
        this.uiPort = uiPort;
        this.asyncHttpClient = asyncHttpClient;
    }

    public void setStoreThresholdMillis(int storeThresholdMillis) throws Exception {
        GeneralConfig generalConfig = getGeneralConfig();
        generalConfig.setStoreThresholdMillis(storeThresholdMillis);
        updateGeneralConfig(generalConfig);
    }

    public String get(String path) throws Exception {
        BoundRequestBuilder request = asyncHttpClient.prepareGet("http://localhost:" + uiPort
                + path);
        Response response = request.execute().get();
        return validateAndReturnBody(response);
    }

    public InputStream getAsStream(String path) throws Exception {
        BoundRequestBuilder request = asyncHttpClient.prepareGet("http://localhost:" + uiPort
                + path);
        Response response = request.execute().get();
        return validateAndReturnBodyAsStream(response);
    }

    private String post(String path, String data) throws Exception {
        BoundRequestBuilder request = asyncHttpClient.preparePost("http://localhost:" + uiPort
                + path);
        request.setBody(data);
        Response response = request.execute().get();
        return validateAndReturnBody(response);
    }

    public GeneralConfig getGeneralConfig() throws Exception {
        return getConfig().getGeneralConfig();
    }

    public void updateGeneralConfig(GeneralConfig config) throws Exception {
        // need to serialize nulls since the /config service treats absence of attribute different
        // from null attribute (the former doesn't update the attribute, the latter sets the
        // attribute to null)
        post("/config/general", GsonFactory.newBuilder().serializeNulls().create().toJson(config));
    }

    public CoarseProfilingConfig getCoarseProfilingConfig() throws Exception {
        return getConfig().getCoarseProfilingConfig();
    }

    public void updateCoarseProfilingConfig(CoarseProfilingConfig config) throws Exception {
        // need to serialize nulls since the /config service treats absence of attribute different
        // from null attribute (the former doesn't update the attribute, the latter sets the
        // attribute to null)
        post("/config/coarse-profiling", GsonFactory.newBuilder().serializeNulls().create()
                .toJson(config));
    }

    public FineProfilingConfig getFineProfilingConfig() throws Exception {
        return getConfig().getFineProfilingConfig();
    }

    public void updateFineProfilingConfig(FineProfilingConfig config) throws Exception {
        // need to serialize nulls since the /config service treats absence of attribute different
        // from null attribute (the former doesn't update the attribute, the latter sets the
        // attribute to null)
        post("/config/fine-profiling",
                GsonFactory.newBuilder().serializeNulls().create().toJson(config));
    }

    public UserConfig getUserConfig() throws Exception {
        return getConfig().getUserConfig();
    }

    public void updateUserConfig(UserConfig config) throws Exception {
        // need to serialize nulls since the /config service treats absence of attribute different
        // from null attribute (the former doesn't update the attribute, the latter sets the
        // attribute to null)
        post("/config/user", GsonFactory.newBuilder().serializeNulls().create().toJson(config));
    }

    @Nullable
    public PluginConfig getPluginConfig(String pluginId) throws Exception {
        Map<String, PluginConfig> pluginConfigs = getConfig().getPluginConfigs();
        if (pluginConfigs == null) {
            return null;
        }
        return pluginConfigs.get(pluginId);
    }

    public void updatePluginConfig(String pluginId, PluginConfig config) throws Exception {
        // need to serialize nulls since the /config service treats absence of attribute different
        // from null attribute (the former doesn't update the attribute, the latter sets the
        // attribute to null)
        post("/config/plugin/" + pluginId, config.toJson());
    }

    public List<PointcutConfig> getPointcutConfigs() throws Exception {
        return getConfig().getPointcutConfigs();
    }

    public String addPointcutConfig(PointcutConfig pointcutConfig) throws Exception {
        String response = post("/config/pointcut/+", gson.toJson(pointcutConfig));
        return new JsonParser().parse(response).getAsString();
    }

    public void updatePointcutConfig(String uniqueHash, PointcutConfig pointcutConfig)
            throws Exception {
        post("/config/pointcut/" + uniqueHash, gson.toJson(pointcutConfig));
    }

    public void removePointcutConfig(String uniqueHash) throws Exception {
        post("/config/pointcut/-", gson.toJson(uniqueHash));
    }

    public Trace getLastTrace() throws Exception {
        return getLastTrace(false);
    }

    public Trace getLastTraceSummary() throws Exception {
        return getLastTrace(true);
    }

    public List<LogMessage> getLogMessages() throws Exception {
        return gson.fromJson(get("/admin/log"), new TypeToken<List<LogMessage>>() {}.getType());
    }

    public void deleteAllLogMessages() throws Exception {
        post("/admin/log/truncate", "");
    }

    private Trace getLastTrace(boolean summary) throws Exception {
        String pointsJson = get("/explorer/points?from=0&to=" + Long.MAX_VALUE + "&low=0&high="
                + Long.MAX_VALUE + "&limit=1000");
        JsonObject response = gson.fromJson(pointsJson, JsonElement.class).getAsJsonObject();
        JsonArray normalPoints = asJsonArrayOrEmpty(response.get("normalPoints"));
        JsonArray errorPoints = asJsonArrayOrEmpty(response.get("errorPoints"));
        JsonArray points = new JsonArray();
        points.addAll(normalPoints);
        points.addAll(errorPoints);
        JsonArray lastTracePoint = getMaxPointByCapturedAt(points);
        if (lastTracePoint == null) {
            throw new AssertionError("no trace found");
        } else {
            String traceId = lastTracePoint.get(2).getAsString();
            if (summary) {
                String traceJson = get("/explorer/summary/" + traceId);
                Trace trace = gson.fromJson(traceJson, Trace.class);
                trace.setSummary(true);
                return trace;
            } else {
                String traceJson = get("/explorer/detail/" + traceId);
                return gson.fromJson(traceJson, Trace.class);
            }
        }
    }

    @Nullable
    private JsonArray getMaxPointByCapturedAt(JsonArray points) {
        long maxCapturedAt = 0;
        JsonArray maxPoint = null;
        for (int i = 0; i < points.size(); i++) {
            JsonArray point = points.get(i).getAsJsonArray();
            long time = point.get(0).getAsLong();
            if (time > maxCapturedAt) {
                maxCapturedAt = time;
                maxPoint = point;
            }
        }
        return maxPoint;
    }

    // this method blocks for an active trace to be available because
    // sometimes need to give container enough time to start up and for the trace to get stuck
    @Nullable
    public Trace getActiveTraceSummary(int timeoutMillis) throws Exception {
        return getActiveTrace(timeoutMillis, true);
    }

    // this method blocks for an active trace to be available because
    // sometimes need to give container enough time to start up and for the trace to get stuck
    @Nullable
    public Trace getActiveTrace(int timeoutMillis) throws Exception {
        return getActiveTrace(timeoutMillis, false);
    }

    private Trace getActiveTrace(int timeoutMillis, boolean summary) throws Exception,
            InterruptedException {
        Stopwatch stopwatch = new Stopwatch().start();
        Trace trace = null;
        // try at least once (e.g. in case timeoutMillis == 0)
        while (true) {
            trace = getActiveTrace(summary);
            if (trace != null || stopwatch.elapsedMillis() > timeoutMillis) {
                break;
            }
            Thread.sleep(20);
        }
        return trace;
    }

    @Nullable
    public List<String> getStackTrace(String stackTraceBlockId) throws Exception {
        String stackTraceJson = get("/block/" + stackTraceBlockId);
        return gson.fromJson(stackTraceJson, new TypeToken<List<String>>() {}.getType());
    }

    @Nullable
    public ExceptionInfo getException(String exceptionBlockId) throws Exception {
        String exceptionJson = get("/block/" + exceptionBlockId);
        return gson.fromJson(exceptionJson, ExceptionInfo.class);
    }

    public void cleanUpAfterEachTest() throws Exception {
        post("/admin/data/truncate", "");
        List<LogMessage> warningMessages = Lists.newArrayList();
        for (LogMessage message : getLogMessages()) {
            if (message.getLevel() == Level.WARN || message.getLevel() == Level.ERROR) {
                warningMessages.add(message);
            }
        }
        if (!warningMessages.isEmpty()) {
            // clear warnings for next test before throwing assertion error
            post("/admin/log/truncate", "");
            throw new AssertionError("There were warnings and/or errors: "
                    + Joiner.on(", ").join(warningMessages));
        }
        post("/admin/config/truncate", "");
    }

    public int getNumPendingCompleteTraces() throws Exception {
        String numPendingCompleteTraces = get("/admin/num-pending-complete-traces");
        return Integer.parseInt(numPendingCompleteTraces);
    }

    public long getNumStoredTraceSnapshots() throws Exception {
        String numStoredTraceSnapshots = get("/admin/num-stored-trace-snapshots");
        return Long.parseLong(numStoredTraceSnapshots);
    }

    @Nullable
    private Trace getActiveTrace(boolean summary) throws Exception {
        String pointsJson = get("/explorer/points?from=0&to=" + Long.MAX_VALUE + "&low=0&high="
                + Long.MAX_VALUE + "&limit=1000");
        JsonArray points = gson.fromJson(pointsJson, JsonElement.class).getAsJsonObject()
                .get("activePoints").getAsJsonArray();
        if (points.size() == 0) {
            return null;
        } else if (points.size() > 1) {
            throw new IllegalStateException("Unexpected number of active traces");
        } else {
            JsonArray values = points.get(0).getAsJsonArray();
            String traceId = values.get(2).getAsString();
            if (summary) {
                String traceJson = get("/explorer/summary/" + traceId);
                Trace trace = gson.fromJson(traceJson, Trace.class);
                trace.setSummary(true);
                return trace;
            } else {
                String traceJson = get("/explorer/detail/" + traceId);
                return gson.fromJson(traceJson, Trace.class);
            }
        }
    }

    private Config getConfig() throws Exception {
        String json = get("/config/read");
        return gson.fromJson(json, Config.class);
    }

    private static JsonArray asJsonArrayOrEmpty(@ReadOnly @Nullable JsonElement jsonElement) {
        if (jsonElement == null || !jsonElement.isJsonArray()) {
            return new JsonArray();
        }
        return jsonElement.getAsJsonArray();
    }

    private static String validateAndReturnBody(Response response) throws IOException {
        if (wasUncompressed(response)) {
            throw new IllegalStateException("HTTP response was not compressed");
        }
        if (response.getStatusCode() == HttpResponseStatus.OK.getCode()) {
            return response.getResponseBody();
        } else {
            throw new IllegalStateException("Unexpected HTTP status code returned: "
                    + response.getStatusCode() + " (" + response.getStatusText() + ")");
        }
    }

    private static InputStream validateAndReturnBodyAsStream(Response response) throws IOException {
        if (wasUncompressed(response)) {
            throw new IllegalStateException("HTTP response was not compressed");
        }
        if (response.getStatusCode() == HttpResponseStatus.OK.getCode()) {
            return response.getResponseBodyAsStream();
        } else {
            throw new IllegalStateException("Unexpected HTTP status code returned: "
                    + response.getStatusCode() + " (" + response.getStatusText() + ")");
        }
    }

    // this method relies on io.informant.testkit.InformantContainer.SaveTheEncodingHandler
    // being inserted into the Netty pipeline before the decompression handler (which removes the
    // Content-Encoding header after decompression) so that the original Content-Encoding can be
    // still be retrieved via the alternate http header X-Original-Content-Encoding
    private static boolean wasUncompressed(Response response) throws AssertionError {
        String contentLength = response.getHeader("Content-Length");
        if ("0".equals(contentLength)) {
            // zero-length responses are never compressed
            return false;
        }
        String contentEncoding = response.getHeader("X-Original-Content-Encoding");
        return !"gzip".equals(contentEncoding);
    }
}
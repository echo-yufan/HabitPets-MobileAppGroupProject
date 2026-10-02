package com.example.pet.app;

import android.os.Handler;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pet.core.ai.ChatRequest;
import com.example.pet.core.ai.LLMClient;
import com.example.pet.core.common.callback.ChatCallback;
import com.example.pet.feature.chat.data.ai.LlmChatGateway;
import com.example.pet.feature.chat.data.local.ChatHistoryStore;
import com.example.pet.feature.chat.data.repository.ChatRepository;
import com.example.pet.feature.chat.domain.model.ChatMessage;
import com.example.pet.feature.chat.domain.model.ChatState;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Real localhost HTTPS requests with test credentials; no production service calls. */
@RunWith(AndroidJUnit4.class)
public class ChatNetworkInstrumentedTest {
    private static final String ANSWER = "{\"choices\":[{\"message\":{\"content\":\"test answer\"}}]}";
    private MockWebServer server;
    private OkHttpClient base;
    private String endpoint;
    private final AtomicBoolean headersReceived = new AtomicBoolean();

    @Before public void setUp() throws Exception {
        HeldCertificate certificate = new HeldCertificate.Builder().commonName("localhost")
                .addSubjectAlternativeName("localhost").build();
        HandshakeCertificates serverCertificates = new HandshakeCertificates.Builder()
                .heldCertificate(certificate).build();
        HandshakeCertificates trust = new HandshakeCertificates.Builder()
                .addTrustedCertificate(certificate.certificate()).build();
        server = new MockWebServer();
        server.useHttps(serverCertificates.sslSocketFactory(), false);
        server.start();
        endpoint = server.url("/chat").toString();
        base = new OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager())
                .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS)
                .eventListener(new EventListener() {
                    @Override public void responseHeadersEnd(Call call, Response response) {
                        headersReceived.set(true);
                    }
                }).build();
    }

    @After public void tearDown() throws Exception {
        if (base != null) {
            base.dispatcher().cancelAll();
            base.connectionPool().evictAll();
        }
        if (server != null) server.shutdown();
    }

    @Test public void productionDefaultsHaveExplicitTimeouts() throws Exception {
        Field field = LLMClient.class.getDeclaredField("DEFAULT_CLIENT");
        field.setAccessible(true);
        OkHttpClient defaults = (OkHttpClient) field.get(null);
        assertEquals(10000, defaults.connectTimeoutMillis());
        assertEquals(10000, defaults.writeTimeoutMillis());
        assertEquals(30000, defaults.readTimeoutMillis());
        assertEquals(60000, defaults.callTimeoutMillis());
    }

    @Test public void successfulResponseReportsExactlyOneReply() throws Exception {
        server.enqueue(new MockResponse().setBody(ANSWER));
        Result result = send(base);
        result.await();
        assertEquals("test answer", result.reply);
        assertNull(result.error);
        assertEquals("Bearer test-only", server.takeRequest(2, TimeUnit.SECONDS).getHeader("Authorization"));
    }

    @Test public void responseBodyReadTimeoutReportsFailureAfterHeadersArrive() throws Exception {
        server.enqueue(new MockResponse().setBody(ANSWER).setBodyDelay(3, TimeUnit.SECONDS));
        Result result = send(base.newBuilder().readTimeout(1, TimeUnit.SECONDS).build());
        result.await();
        assertTrue("Must exercise body reading, not connection failure", headersReceived.get());
        assertEquals("请求超时，请重试", result.error);
    }

    @Test public void totalTimeoutStopsBodyEvenWhileBytesKeepArriving() throws Exception {
        // Use small, flushed HTTP/1.1 chunks to exercise a continuously arriving body.
        OkHttpClient streaming = base.newBuilder()
                .protocols(Collections.singletonList(Protocol.HTTP_1_1)).build();
        server.enqueue(new MockResponse().setBody(ANSWER));
        send(streaming).await(); // warm TLS before timing the body transfer
        headersReceived.set(false);
        // QueueDispatcher.peek() also throttles READING the request body. Use the
        // default unrestricted peek and throttle only the dispatched response.
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setBody(ANSWER).throttleBody(1, 100, TimeUnit.MILLISECONDS);
            }
        });
        long start = android.os.SystemClock.elapsedRealtime();
        Result result = send(streaming.newBuilder().callTimeout(3, TimeUnit.SECONDS).build());
        result.await();
        assertTrue(headersReceived.get());
        assertEquals("请求超时，请重试", result.error);
        assertTrue(android.os.SystemClock.elapsedRealtime() - start < 5500);
    }

    @Test public void waitingForHeadersTimesOutAndReportsFailure() throws Exception {
        server.enqueue(new MockResponse().setBody(ANSWER).setHeadersDelay(4, TimeUnit.SECONDS));
        Result result = send(base.newBuilder().callTimeout(1500, TimeUnit.MILLISECONDS).build());
        result.await();
        assertFalse(headersReceived.get());
        assertEquals("请求超时，请重试", result.error);
    }

    @Test public void httpFailureReportsOneTerminalError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("rate limited"));
        Result result = send(base);
        result.await();
        assertTrue(result.error.contains("HTTP 429"));
    }

    @Test public void malformedJsonReportsOneTerminalError() throws Exception {
        server.enqueue(new MockResponse().setBody("not json"));
        Result result = send(base);
        result.await();
        assertEquals("回复解析失败，请重试", result.error);
    }

    @Test public void cancellationReportsFailureAndReleasesRequest() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        Result result = new Result();
        ChatRequest request = client(base).sendChat(messages(), result);
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS));
        request.cancel();
        result.await();
        assertEquals("请求已取消", result.error);
    }

    @Test public void actualTimeoutEndsRepositoryBusyAndRetryAddsExactlyOneAnswer() throws Exception {
        server.enqueue(new MockResponse().setBody(ANSWER).setBodyDelay(3, TimeUnit.SECONDS));
        OkHttpClient timed = base.newBuilder().readTimeout(1, TimeUnit.SECONDS).build();
        CountDownLatch failed = new CountDownLatch(1), succeeded = new CountDownLatch(1);
        ChatRepository repository = main(() -> {
            ChatRepository repo = new ChatRepository(new MemoryStore(), new LlmChatGateway(client(timed)),
                    new Handler(Looper.getMainLooper())::post);
            repo.addListener(state -> {
                if (state.retryMessageId() != null) failed.countDown();
                if (state.messages.size() == 2) succeeded.countDown();
            });
            repo.sendMessage("retry this");
            return repo;
        });
        assertTrue(failed.await(8, TimeUnit.SECONDS));
        String id = main(() -> {
            assertFalse(repository.snapshot().requesting);
            assertEquals(ChatMessage.Status.FAILED, repository.snapshot().messages.get(0).status);
            return repository.snapshot().retryMessageId();
        });
        server.enqueue(new MockResponse().setBody(ANSWER));
        assertTrue(main(() -> repository.retry(id)));
        assertTrue(succeeded.await(8, TimeUnit.SECONDS));
        main(() -> {
            assertFalse(repository.snapshot().requesting);
            assertEquals(id, repository.snapshot().messages.get(0).id);
            assertEquals(2, repository.snapshot().messages.size());
            return null;
        });
    }

    private LLMClient client(OkHttpClient http) {
        return new LLMClient(http, endpoint, "test-only");
    }
    private Result send(OkHttpClient http) throws Exception {
        Result result = new Result();
        client(http).sendChat(messages(), result);
        return result;
    }
    private JSONArray messages() throws Exception {
        return new JSONArray().put(new JSONObject().put("role", "user").put("content", "test"));
    }
    private static <T> T main(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
        return task.get(5, TimeUnit.SECONDS);
    }
    private static final class Result implements ChatCallback {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger count = new AtomicInteger();
        volatile String reply;
        volatile String error;
        @Override public void onSuccess(String reply) { this.reply = reply; count.incrementAndGet(); done.countDown(); }
        @Override public void onError(String error) { this.error = error; count.incrementAndGet(); done.countDown(); }
        void await() throws Exception {
            assertTrue("Request never completed", done.await(8, TimeUnit.SECONDS));
            assertEquals(1, count.get());
        }
    }
    private static final class MemoryStore implements ChatHistoryStore {
        @Override public ChatState load() { return null; }
        @Override public void save(ChatState state) { }
    }
}

package ru.shatrev.gateway.security;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** Runs before NettyWriteResponseFilter so its response body cannot leak an internal 401. */
@Component
public class TasksUnauthorizedResponseFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(TasksUnauthorizedResponseFilter.class);
    private static final byte[] ERROR = "{\"error\":{\"code\":\"UPSTREAM_UNAVAILABLE\",\"message\":\"Сервис временно недоступен. Попробуйте позже\",\"details\":null}}"
            .getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (exchange.getAttribute(JwtValidationFilter.USER_ID_ATTRIBUTE) == null
                || !(path.startsWith("/api/v1/branches") || path.startsWith("/api/v1/tasks"))) {
            return chain.filter(exchange);
        }
        var response = new ServerHttpResponseDecorator(exchange.getResponse()) {
            private boolean internalUnauthorized;

            @Override
            public boolean setStatusCode(HttpStatusCode status) {
                if (status.value() == 401) {
                    internalUnauthorized = true;
                    log.warn("tasks rejected internal context; correlationId={}",
                            exchange.getRequest().getHeaders().getFirst("X-Correlation-Id"));
                    return super.setStatusCode(HttpStatus.BAD_GATEWAY);
                }
                return super.setStatusCode(status);
            }

            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (!internalUnauthorized) return super.writeWith(body);
                getHeaders().setContentType(MediaType.APPLICATION_JSON);
                getHeaders().remove("Content-Length");
                getHeaders().remove("WWW-Authenticate");
                return Flux.from(body).doOnNext(DataBufferUtils::release).then(Mono.defer(() ->
                        super.writeWith(Mono.just(bufferFactory().wrap(ERROR)))));
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMapSequential(Flux::from));
            }
        };
        return chain.filter(exchange.mutate().response(response).build());
    }

    @Override
    public int getOrder() { return -50; }
}

package com.plantogether.destination.grpc.client;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.trip.grpc.IsMemberRequest;
import com.plantogether.trip.grpc.IsMemberResponse;
import com.plantogether.trip.grpc.TripServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TripGrpcClient {

    @Value("${grpc.trip-service.host:localhost}")
    private String host;

    @Value("${grpc.trip-service.port:9081}")
    private int port;

    private ManagedChannel channel;
    private TripServiceGrpc.TripServiceBlockingStub stub;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        stub = TripServiceGrpc.newBlockingStub(channel);
        log.info("TripGrpcClient initialized → {}:{}", host, port);
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    public boolean isMember(String tripId, String deviceId) {
        try {
            IsMemberResponse resp = stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .isMember(IsMemberRequest.newBuilder()
                            .setTripId(tripId)
                            .setDeviceId(deviceId)
                            .build());
            return resp.getIsMember();
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            log.error("IsMember gRPC failed trip={} device={}: {}", tripId, deviceId, e.getStatus());
            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "trip-service unavailable");
            }
            if (code == Status.Code.PERMISSION_DENIED || code == Status.Code.NOT_FOUND) {
                throw new AccessDeniedException("Unable to verify trip membership");
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "trip-service error: " + code);
        }
    }

    void setStub(TripServiceGrpc.TripServiceBlockingStub stub) {
        this.stub = stub;
    }
}

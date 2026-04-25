package com.plantogether.destination.grpc.client;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.trip.grpc.GetTripMembersRequest;
import com.plantogether.trip.grpc.GetTripMembersResponse;
import com.plantogether.trip.grpc.IsMemberRequest;
import com.plantogether.trip.grpc.IsMemberResponse;
import com.plantogether.trip.grpc.TripMemberProto;
import com.plantogether.trip.grpc.TripServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

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
    channel =
        ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
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
      IsMemberResponse resp =
          stub.withWaitForReady()
              .withDeadlineAfter(2, TimeUnit.SECONDS)
              .isMember(
                  IsMemberRequest.newBuilder().setTripId(tripId).setDeviceId(deviceId).build());
      return resp.getIsMember();
    } catch (StatusRuntimeException e) {
      handleStatusRuntimeException(e, tripId, deviceId);
      throw new AccessDeniedException("Unable to verify trip membership");
    }
  }

  public IsMemberResponse isMemberWithRole(String tripId, String deviceId) {
    try {
      return stub.withWaitForReady()
          .withDeadlineAfter(2, TimeUnit.SECONDS)
          .isMember(IsMemberRequest.newBuilder().setTripId(tripId).setDeviceId(deviceId).build());
    } catch (StatusRuntimeException e) {
      handleStatusRuntimeException(e, tripId, deviceId);
      throw new AccessDeniedException("Unable to verify trip membership");
    }
  }

  private void handleStatusRuntimeException(
      StatusRuntimeException e, String tripId, String deviceId) {
    Status.Code code = e.getStatus().getCode();
    log.error("IsMember gRPC failed trip={} device={}: {}", tripId, deviceId, e.getStatus());
    switch (code) {
      case UNAVAILABLE, DEADLINE_EXCEEDED ->
          throw new ResponseStatusException(
              HttpStatus.SERVICE_UNAVAILABLE, "trip-service unavailable");
      case INTERNAL, UNKNOWN ->
          throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "trip-service error");
      case PERMISSION_DENIED ->
          throw new AccessDeniedException("Permission denied by trip-service");
      default -> {
        // Fall through — caller will throw AccessDeniedException.
      }
    }
  }

  public List<TripMemberProto> getTripMembers(String tripId) {
    try {
      GetTripMembersResponse resp =
          stub.withWaitForReady()
              .withDeadlineAfter(2, TimeUnit.SECONDS)
              .getTripMembers(GetTripMembersRequest.newBuilder().setTripId(tripId).build());
      return resp.getMembersList();
    } catch (StatusRuntimeException e) {
      Status.Code code = e.getStatus().getCode();
      log.error("GetTripMembers gRPC failed trip={}: {}", tripId, e.getStatus());
      switch (code) {
        case UNAVAILABLE, DEADLINE_EXCEEDED ->
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "trip-service unavailable");
        case NOT_FOUND -> throw new ResourceNotFoundException("Trip not found: " + tripId);
        case INTERNAL, UNKNOWN ->
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "trip-service error");
        case RESOURCE_EXHAUSTED ->
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "trip-service quota exhausted");
        case UNAUTHENTICATED ->
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "trip-service authentication failed");
        case PERMISSION_DENIED ->
            throw new AccessDeniedException("Permission denied by trip-service");
        case CANCELLED ->
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "trip-service cancelled");
        default -> throw new AccessDeniedException("Unable to resolve trip members");
      }
    }
  }

  public void setStub(TripServiceGrpc.TripServiceBlockingStub stub) {
    this.stub = stub;
  }
}

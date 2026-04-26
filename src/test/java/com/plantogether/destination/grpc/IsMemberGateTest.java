package com.plantogether.destination.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantogether.destination.controller.DestinationController;
import com.plantogether.destination.exception.GlobalExceptionHandler;
import com.plantogether.destination.grpc.client.TripGrpcClient;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.repository.DestinationRepository;
import com.plantogether.destination.repository.DestinationVoteConfigRepository;
import com.plantogether.destination.repository.DestinationVoteRepository;
import com.plantogether.destination.service.DestinationService;
import com.plantogether.destination.service.DestinationVoteConfigService;
import com.plantogether.destination.service.DestinationVoteService;
import com.plantogether.destination.service.TripLockService;
import com.plantogether.trip.grpc.IsMemberRequest;
import com.plantogether.trip.grpc.IsMemberResponse;
import com.plantogether.trip.grpc.TripServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Integration test verifying destination-service enforces the IsMember gRPC gate before accepting
 * any write operation.
 *
 * <p>Pattern: InProcessServerBuilder + standalone MockMvc.
 */
class IsMemberGateTest {

  private static final String SERVER_NAME = "test-trip-grpc-" + UUID.randomUUID();
  private static final String DEVICE_ID = UUID.randomUUID().toString();

  private Server mockTripServer;
  private ManagedChannel channel;
  private CapturingTripServiceImpl mockTripService;
  private MockMvc mockMvc;
  private Authentication authentication;
  private DestinationRepository repository;
  private DestinationVoteConfigRepository configRepository;

  @BeforeEach
  void setUp() throws IOException {
    mockTripService = new CapturingTripServiceImpl();
    mockTripServer =
        InProcessServerBuilder.forName(SERVER_NAME)
            .directExecutor()
            .addService(mockTripService)
            .build()
            .start();

    channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();

    TripGrpcClient tripGrpcClient = new TripGrpcClient();
    tripGrpcClient.setStub(TripServiceGrpc.newBlockingStub(channel));

    repository = mock(DestinationRepository.class);
    DestinationVoteRepository voteRepository = mock(DestinationVoteRepository.class);
    configRepository = mock(DestinationVoteConfigRepository.class);

    when(repository.save(any(Destination.class)))
        .thenAnswer(
            inv -> {
              Destination d = inv.getArgument(0);
              d.setId(UUID.randomUUID());
              d.setCreatedAt(Instant.now());
              d.setUpdatedAt(Instant.now());
              return d;
            });
    when(voteRepository.findByDestinationIdIn(any())).thenReturn(List.of());

    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    TripLockService tripLockService = mock(TripLockService.class);

    DestinationService service = new DestinationService(repository, voteRepository, tripGrpcClient);
    DestinationVoteConfigService voteConfigService =
        new DestinationVoteConfigService(
            configRepository, voteRepository, repository, tripGrpcClient, tripLockService);
    DestinationVoteService voteService =
        new DestinationVoteService(
            repository,
            voteRepository,
            configRepository,
            tripGrpcClient,
            eventPublisher,
            tripLockService);

    DestinationController controller = new DestinationController(service, voteConfigService);

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    authentication =
        new UsernamePasswordAuthenticationToken(
            DEVICE_ID, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    SecurityContextHolder.clearContext();
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    mockTripServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  private String validBody() {
    return """
    {
      "name": "Paris",
      "description": "City of lights",
      "estimatedBudget": 1200.00,
      "currency": "EUR"
    }
    """;
  }

  @Test
  void propose_byMember_isForwardedToBusiness() throws Exception {
    UUID tripId = UUID.randomUUID();
    mockTripService.stubIsMember(true, "PARTICIPANT");

    mockMvc
        .perform(
            post("/api/v1/trips/{tripId}/destinations", tripId)
                .principal(authentication)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
        .andExpect(status().isCreated());

    assertThat(mockTripService.callCount).isEqualTo(1);
    assertThat(mockTripService.lastRequest.getTripId()).isEqualTo(tripId.toString());
    verify(repository).save(any(Destination.class));
  }

  @Test
  void propose_byNonMember_returns403() throws Exception {
    UUID tripId = UUID.randomUUID();
    mockTripService.stubIsMember(false, "");

    mockMvc
        .perform(
            post("/api/v1/trips/{tripId}/destinations", tripId)
                .principal(authentication)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
        .andExpect(status().isForbidden());

    assertThat(mockTripService.callCount).isEqualTo(1);
    verify(repository, never()).save(any(Destination.class));
  }

  @Test
  void putVoteConfig_byOrganizer_forwarded() throws Exception {
    UUID tripId = UUID.randomUUID();
    mockTripService.stubIsMember(true, "ORGANIZER");
    when(configRepository.findById(tripId)).thenReturn(Optional.empty());
    when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    mockMvc
        .perform(
            put("/api/v1/trips/{tripId}/destinations/vote-config", tripId)
                .principal(authentication)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"RANKING\"}"))
        .andExpect(status().isOk());

    assertThat(mockTripService.callCount).isEqualTo(1);
    verify(configRepository).save(any());
  }

  @Test
  void putVoteConfig_byParticipant_returns403() throws Exception {
    UUID tripId = UUID.randomUUID();
    mockTripService.stubIsMember(true, "PARTICIPANT");

    mockMvc
        .perform(
            put("/api/v1/trips/{tripId}/destinations/vote-config", tripId)
                .principal(authentication)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"SIMPLE\"}"))
        .andExpect(status().isForbidden());

    verify(configRepository, never()).save(any());
  }

  static class CapturingTripServiceImpl extends TripServiceGrpc.TripServiceImplBase {

    boolean memberResult;
    String role;
    int callCount;
    IsMemberRequest lastRequest;

    void stubIsMember(boolean isMember, String role) {
      this.memberResult = isMember;
      this.role = role;
      this.callCount = 0;
      this.lastRequest = null;
    }

    @Override
    public void isMember(
        IsMemberRequest request, StreamObserver<IsMemberResponse> responseObserver) {
      callCount++;
      lastRequest = request;
      responseObserver.onNext(
          IsMemberResponse.newBuilder()
              .setIsMember(memberResult)
              .setRole(role != null ? role : "")
              .build());
      responseObserver.onCompleted();
    }
  }
}

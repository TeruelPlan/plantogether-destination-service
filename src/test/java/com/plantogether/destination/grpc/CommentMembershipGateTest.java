package com.plantogether.destination.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantogether.destination.controller.DestinationCommentController;
import com.plantogether.destination.exception.GlobalExceptionHandler;
import com.plantogether.destination.grpc.client.TripGrpcClient;
import com.plantogether.destination.model.Destination;
import com.plantogether.destination.model.DestinationComment;
import com.plantogether.destination.repository.DestinationCommentRepository;
import com.plantogether.destination.repository.DestinationRepository;
import com.plantogether.destination.service.DestinationCommentService;
import com.plantogether.trip.grpc.GetTripMembersRequest;
import com.plantogether.trip.grpc.GetTripMembersResponse;
import com.plantogether.trip.grpc.TripMemberProto;
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

/** Membership gate coverage for the comment endpoints (Story 4.4). */
class CommentMembershipGateTest {

  private static final String SERVER_NAME = "test-trip-grpc-" + UUID.randomUUID();
  private static final String DEVICE_ID = UUID.randomUUID().toString();

  private Server mockTripServer;
  private ManagedChannel channel;
  private CapturingTripService mockTripService;
  private MockMvc mockMvc;
  private Authentication authentication;
  private DestinationRepository destinationRepository;
  private DestinationCommentRepository commentRepository;
  private UUID tripId;
  private UUID destinationId;

  @BeforeEach
  void setUp() throws IOException {
    mockTripService = new CapturingTripService();
    mockTripServer =
        InProcessServerBuilder.forName(SERVER_NAME)
            .directExecutor()
            .addService(mockTripService)
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();

    TripGrpcClient tripGrpcClient = new TripGrpcClient();
    tripGrpcClient.setStub(TripServiceGrpc.newBlockingStub(channel));

    destinationRepository = mock(DestinationRepository.class);
    commentRepository = mock(DestinationCommentRepository.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    tripId = UUID.randomUUID();
    destinationId = UUID.randomUUID();
    Destination dest =
        Destination.builder()
            .id(destinationId)
            .tripId(tripId)
            .name("Paris")
            .proposedBy(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(dest));
    when(commentRepository.findByDestinationIdOrderByCreatedAtAscIdAsc(destinationId))
        .thenReturn(List.of());
    when(commentRepository.save(any(DestinationComment.class)))
        .thenAnswer(
            inv -> {
              DestinationComment c = inv.getArgument(0);
              c.setId(UUID.randomUUID());
              c.setCreatedAt(Instant.now());
              return c;
            });

    DestinationCommentService service =
        new DestinationCommentService(
            destinationRepository, commentRepository, tripGrpcClient, eventPublisher);
    DestinationCommentController controller = new DestinationCommentController(service);

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

  @Test
  void postComment_byMember_forwarded() throws Exception {
    mockTripService.stubMembership(true);

    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/comments", destinationId)
                .principal(authentication)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\"}"))
        .andExpect(status().isCreated());

    assertThat(mockTripService.getTripMembersCalls).isEqualTo(1);
    verify(commentRepository).save(any(DestinationComment.class));
  }

  @Test
  void postComment_byNonMember_returns403() throws Exception {
    mockTripService.stubMembership(false);

    mockMvc
        .perform(
            post("/api/v1/destinations/{destinationId}/comments", destinationId)
                .principal(authentication)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\"}"))
        .andExpect(status().isForbidden());

    verify(commentRepository, never()).save(any(DestinationComment.class));
  }

  @Test
  void getComments_byMember_forwarded() throws Exception {
    mockTripService.stubMembership(true);

    mockMvc
        .perform(
            get("/api/v1/destinations/{destinationId}/comments", destinationId)
                .principal(authentication)
                .header("X-Device-Id", DEVICE_ID))
        .andExpect(status().isOk());

    assertThat(mockTripService.getTripMembersCalls).isEqualTo(1);
    verify(commentRepository).findByDestinationIdOrderByCreatedAtAscIdAsc(destinationId);
  }

  @Test
  void getComments_byNonMember_returns403() throws Exception {
    mockTripService.stubMembership(false);

    mockMvc
        .perform(
            get("/api/v1/destinations/{destinationId}/comments", destinationId)
                .principal(authentication)
                .header("X-Device-Id", DEVICE_ID))
        .andExpect(status().isForbidden());

    verify(commentRepository, never()).findByDestinationIdOrderByCreatedAtAscIdAsc(any());
  }

  static class CapturingTripService extends TripServiceGrpc.TripServiceImplBase {
    boolean memberResult;
    int getTripMembersCalls;

    void stubMembership(boolean isMember) {
      this.memberResult = isMember;
      this.getTripMembersCalls = 0;
    }

    @Override
    public void getTripMembers(
        GetTripMembersRequest request, StreamObserver<GetTripMembersResponse> obs) {
      getTripMembersCalls++;
      GetTripMembersResponse.Builder resp = GetTripMembersResponse.newBuilder();
      if (memberResult) {
        resp.addMembers(
            TripMemberProto.newBuilder()
                .setDeviceId(DEVICE_ID)
                .setDisplayName("Alice")
                .setRole("PARTICIPANT")
                .build());
      } else {
        resp.addMembers(
            TripMemberProto.newBuilder()
                .setDeviceId(UUID.randomUUID().toString())
                .setDisplayName("Someone else")
                .setRole("PARTICIPANT")
                .build());
      }
      obs.onNext(resp.build());
      obs.onCompleted();
    }
  }
}

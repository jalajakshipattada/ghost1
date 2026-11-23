package com.ghostcommunity.backend;

import com.ghostcommunity.backend.controller.DirectMessageController;
import com.ghostcommunity.backend.controller.PollController;
import com.ghostcommunity.backend.model.*;
import com.ghostcommunity.backend.payload.ChatResponsePayload;
import com.ghostcommunity.backend.repository.*;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatPollAndRejectRevealTest {

    @Mock
    private PollRepository pollRepository;

    @Mock
    private PollOptionRepository pollOptionRepository;

    @Mock
    private PollVoteRepository pollVoteRepository;

    @Mock
    private DirectMessageRepository dmRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private PollController pollController;

    @InjectMocks
    private DirectMessageController directMessageController;

    private User me;
    private User partner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        me = User.builder()
                .id(1L)
                .email("me@test.com")
                .ghostName("PhantomMe")
                .realName("Real Me")
                .build();

        partner = User.builder()
                .id(2L)
                .email("partner@test.com")
                .ghostName("ShadowPartner")
                .realName("Real Partner")
                .build();

        UserDetailsImpl userDetails = UserDetailsImpl.build(me);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(partner));
    }

    @Test
    void testCreatePollSuccess() {
        PollController.CreatePollRequest req = new PollController.CreatePollRequest(
                "Best coding language?",
                List.of("Java", "Dart", "Rust")
        );

        when(pollRepository.save(any(Poll.class))).thenAnswer(inv -> {
            Poll p = inv.getArgument(0);
            return Poll.builder().id(100L).question(p.getQuestion()).author(p.getAuthor()).build();
        });

        when(pollOptionRepository.save(any(PollOption.class))).thenAnswer(inv -> {
            PollOption opt = inv.getArgument(0);
            return PollOption.builder().id(new Random().nextLong(1000) + 1).text(opt.getText()).poll(opt.getPoll()).voteCount(0).build();
        });

        ResponseEntity<?> response = pollController.createPoll(req);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals(100L, body.get("pollId"));
        assertEquals("Best coding language?", body.get("question"));
        List<?> options = (List<?>) body.get("options");
        assertEquals(3, options.size());
    }

    @Test
    void testGetPollWithVoteStatus() {
        Poll poll = Poll.builder().id(50L).question("Lunch plan?").author(partner).build();
        PollOption opt1 = PollOption.builder().id(11L).text("Pizza").poll(poll).voteCount(3).build();
        PollOption opt2 = PollOption.builder().id(12L).text("Burger").poll(poll).voteCount(2).build();

        when(pollRepository.findById(50L)).thenReturn(Optional.of(poll));
        when(pollOptionRepository.findByPollIdOrderByIdAsc(50L)).thenReturn(List.of(opt1, opt2));

        PollVote myVote = PollVote.builder().id(99L).poll(poll).option(opt1).user(me).build();
        when(pollVoteRepository.findByPollIdAndUserId(50L, 1L)).thenReturn(Optional.of(myVote));

        ResponseEntity<?> response = pollController.getPoll(50L);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals(50L, body.get("id"));
        assertEquals(5, body.get("totalVotes"));
        assertEquals(true, body.get("hasVoted"));
        assertEquals(11L, body.get("votedOptionId"));
    }

    @Test
    void testVoteOnPoll_FirstVoteAndSwitchVote() {
        Poll poll = Poll.builder().id(60L).question("Favorite color?").author(me).build();
        PollOption optRed = PollOption.builder().id(101L).text("Red").poll(poll).voteCount(0).build();
        PollOption optBlue = PollOption.builder().id(102L).text("Blue").poll(poll).voteCount(0).build();

        when(pollRepository.findById(60L)).thenReturn(Optional.of(poll));
        when(pollOptionRepository.findById(101L)).thenReturn(Optional.of(optRed));
        when(pollOptionRepository.findById(102L)).thenReturn(Optional.of(optBlue));
        when(pollVoteRepository.findByPollIdAndUserId(60L, 1L)).thenReturn(Optional.empty());

        // 1. Cast first vote on Red
        ResponseEntity<?> resp1 = pollController.voteOnPoll(60L, 101L);
        assertEquals(HttpStatus.OK, resp1.getStatusCode());
        assertEquals(1, optRed.getVoteCount());
        verify(pollVoteRepository, times(1)).save(any(PollVote.class));

        // 2. Mock that user now has voted for Red
        PollVote existingVote = PollVote.builder().id(1L).poll(poll).option(optRed).user(me).build();
        when(pollVoteRepository.findByPollIdAndUserId(60L, 1L)).thenReturn(Optional.of(existingVote));

        // 3. Switch vote from Red to Blue
        ResponseEntity<?> resp2 = pollController.voteOnPoll(60L, 102L);
        assertEquals(HttpStatus.OK, resp2.getStatusCode());
        assertEquals(0, optRed.getVoteCount());
        assertEquals(1, optBlue.getVoteCount());
        assertEquals(optBlue, existingVote.getOption());
    }

    @Test
    void testRejectRevealRequestInsertsSystemMessageAndBroadcasts() {
        when(dmRepository.save(any(DirectMessage.class))).thenAnswer(inv -> {
            DirectMessage dm = inv.getArgument(0);
            return DirectMessage.builder()
                    .id(555L)
                    .sender(dm.getSender())
                    .receiver(dm.getReceiver())
                    .content(dm.getContent())
                    .messageType(dm.getMessageType())
                    .chatMode(dm.getChatMode())
                    .build();
        });

        ResponseEntity<?> response = directMessageController.rejectReveal(2L);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify DM saved with SYSTEM type and content
        ArgumentCaptor<DirectMessage> dmCaptor = ArgumentCaptor.forClass(DirectMessage.class);
        verify(dmRepository).save(dmCaptor.capture());
        DirectMessage saved = dmCaptor.getValue();
        assertEquals("SYSTEM", saved.getMessageType());
        assertEquals("Identity reveal request was declined.", saved.getContent());
        assertEquals("GHOST", saved.getChatMode());
        assertEquals(1L, saved.getSender().getId());
        assertEquals(2L, saved.getReceiver().getId());

        // Verify WebSocket broadcasts sent to both parties
        verify(messagingTemplate).convertAndSend(eq("/topic/user/2"), any(ChatResponsePayload.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/user/1"), any(ChatResponsePayload.class));
    }
}

package com.oldbutgold.shop.modules.communication.api;
import com.oldbutgold.shop.modules.communication.application.ChatService;
import com.oldbutgold.shop.modules.communication.application.OfferService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import java.util.*;
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    private final ChatService chat;private final OfferService offers;
    public ChatController(ChatService chat,OfferService offers){this.chat=chat;this.offers=offers;}
    @GetMapping("/conversations") public ChatDtos.Conversations list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return chat.list(id(jwt),query,page,size);}
    @PostMapping("/conversations") public ChatDtos.Detail start(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ChatDtos.Start body){return chat.start(id(jwt),body);}
    @GetMapping("/conversations/{conversationId}") public ChatDtos.Detail detail(@AuthenticationPrincipal Jwt jwt,@PathVariable long conversationId,@RequestParam(required=false) Long before,@RequestParam(defaultValue="50") int size){return chat.detail(id(jwt),conversationId,before,size);}
    @PostMapping("/conversations/{conversationId}/messages") public ChatDtos.Message send(@AuthenticationPrincipal Jwt jwt,@PathVariable long conversationId,@Valid @RequestBody ChatDtos.Send body){return chat.send(id(jwt),conversationId,body);}
    @PostMapping("/conversations/{conversationId}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@AuthenticationPrincipal Jwt jwt,@PathVariable long conversationId,@Valid @RequestBody ChatDtos.Read body){chat.read(id(jwt),conversationId,body.messageId());}
    @PostMapping("/conversations/{conversationId}/offers") public ChatDtos.Offer propose(@AuthenticationPrincipal Jwt jwt,@PathVariable long conversationId,@Valid @RequestBody ChatDtos.Proposal body){return offers.propose(id(jwt),conversationId,body);}
    @PostMapping("/offers/{offerId}/decision") public ChatDtos.Offer decide(@AuthenticationPrincipal Jwt jwt,@PathVariable long offerId,@Valid @RequestBody ChatDtos.Decide body){return offers.decide(id(jwt),offerId,body);}
    @GetMapping("/blocks") public List<ChatDtos.Block> blocks(@AuthenticationPrincipal Jwt jwt){return chat.blocks(id(jwt));}
    @GetMapping("/blocks/search") public List<Map<String,Object>> search(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="") String query){return chat.searchBuyers(id(jwt),query);}
    @PostMapping("/blocks") public List<ChatDtos.Block> block(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ChatDtos.BlockInput body){return chat.block(id(jwt),body);}
    private long id(Jwt jwt){return Long.parseLong(jwt.getSubject());}
}

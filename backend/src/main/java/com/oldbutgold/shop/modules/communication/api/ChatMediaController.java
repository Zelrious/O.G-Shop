package com.oldbutgold.shop.modules.communication.api;
import com.oldbutgold.shop.modules.communication.application.ChatMediaService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/chat")
public class ChatMediaController {
    private final ChatMediaService media;
    public ChatMediaController(ChatMediaService media){this.media=media;}
    @PostMapping(value="/conversations/{conversationId}/media",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatDtos.Message send(@AuthenticationPrincipal Jwt jwt,@PathVariable long conversationId,@RequestParam UUID clientId,@RequestPart MultipartFile file,@RequestParam(defaultValue="") String caption){return media.send(Long.parseLong(jwt.getSubject()),conversationId,clientId,file,caption);}
    @GetMapping("/messages/{messageId}/media")
    public ResponseEntity<byte[]> read(@AuthenticationPrincipal Jwt jwt,@PathVariable long messageId){var file=media.read(Long.parseLong(jwt.getSubject()),messageId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mime())).contentLength(file.bytes().length).header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline").cacheControl(CacheControl.noStore()).body(file.bytes());}
}

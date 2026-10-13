package com.oldbutgold.shop.modules.communication.application;
import com.oldbutgold.shop.modules.communication.api.ChatDtos.Message;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatRepository;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatRepository.StoredMessage;
import com.oldbutgold.shop.modules.communication.infrastructure.media.ChatMediaStorage;
import com.oldbutgold.shop.modules.communication.infrastructure.media.ChatMediaStorage.Asset;
import com.oldbutgold.shop.modules.commerce.application.ConversationCommerceFacade;
import com.oldbutgold.shop.modules.trustsafety.application.ConversationEvidenceFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.util.*;
import static com.oldbutgold.shop.modules.communication.application.ChatService.problem;
@Service
public class ChatMediaService {
    private static final Logger log=LoggerFactory.getLogger(ChatMediaService.class);
    private final ChatService chat;private final ChatRepository repository;private final ChatMediaStorage storage;
    private final ConversationCommerceFacade commerce;private final ConversationEvidenceFacade evidence;private final Clock clock;private final TransactionTemplate tx;
    public ChatMediaService(ChatService chat,ChatRepository repository,ChatMediaStorage storage,ConversationCommerceFacade commerce,ConversationEvidenceFacade evidence,Clock clock,PlatformTransactionManager manager){
        this.chat=chat;this.repository=repository;this.storage=storage;this.commerce=commerce;this.evidence=evidence;this.clock=clock;this.tx=new TransactionTemplate(manager);
    }
    public record MediaFile(byte[] bytes,String mime) {}
    public Message send(long actor,long conversation,UUID client,MultipartFile file,String caption){
        if(client==null)throw problem(400,"CHAT_CLIENT_ID_REQUIRED","Cần mã gửi tệp để thử lại an toàn.");
        if(file.getSize()==0||file.getSize()>20971520L)throw problem(400,"CHAT_MEDIA_SIZE","Tệp tối đa20MB; ảnh tối đa5MB.");
        String text=caption==null?"":caption.strip();if(text.length()>2000)throw problem(400,"CHAT_TEXT_INVALID","Chú thích tối đa2000 ký tự.");
        byte[] bytes;try{bytes=file.getBytes();}catch(Exception e){throw problem(400,"CHAT_MEDIA_INVALID","Không đọc được tệp gửi lên.");}
        var validated=ChatMediaValidator.validate(bytes,file.getContentType());
        final String content=text.isEmpty()?(validated.type().equals("IMAGE")?"Đã gửi ảnh.":"Đã gửi video."):text;
        StoredMessage replay=tx.execute(status->{
            var initial=chat.owned(actor,conversation,false);chat.lockParties(initial);var c=chat.owned(actor,conversation,true);
            var existing=repository.replay(conversation,client);if(existing.isPresent()){verify(existing.get(),actor,validated,content);return existing.get();}
            chat.requireSending(actor,c);var q=repository.quota(conversation,actor);
            if(q.images()+(validated.type().equals("IMAGE")?1:0)>5||q.videos()+(validated.type().equals("VIDEO")?1:0)>1||q.bytes()+bytes.length>47185920L)throw problem(429,"CHAT_MEDIA_QUOTA","Đã vượt hạn mức media của hội thoại hôm nay.");
            return null;
        });
        if(replay!=null)return chat.message(replay);
        // Provider I/O is outside the command transaction. Recheck permission and
        // quota under the same party/conversation locks before persistence.
        Asset asset=storage.store(bytes,validated.mime(),validated.type());
        long message;
        try{
            Long result=tx.execute(status->{
                var initial=chat.owned(actor,conversation,false);chat.lockParties(initial);var c=chat.owned(actor,conversation,true);
                var existing=repository.replay(conversation,client);if(existing.isPresent()){verify(existing.get(),actor,validated,content);return existing.get().id();}
                chat.requireSending(actor,c);repository.consumeQuota(conversation,actor,validated.type(),bytes.length);
                Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("managedBy","CHAT_V31");metadata.put("provider",asset.provider());metadata.put("mime",asset.mime());metadata.put("bytes",bytes.length);metadata.put("sha256",validated.sha256());if(validated.duration()!=null)metadata.put("duration",validated.duration());
                long id=repository.append(conversation,actor,client,validated.type(),content,null,null,asset.key(),metadata,clock.instant().plus(Duration.ofDays(7)));
                chat.notifyMessage(c,actor,id,"Media mới trong hội thoại",validated.type().equals("IMAGE")?"Đối tác đã gửi hình ảnh.":"Đối tác đã gửi video.","CHAT_MEDIA");
                return id;
            });
            message=Objects.requireNonNull(result);
        }catch(RuntimeException e){deleteOrWarn(asset);throw e;}
        StoredMessage saved=repository.message(message).orElseThrow();
        if(!Objects.equals(saved.object(),asset.key()))deleteOrWarn(asset);
        return chat.message(saved);
    }
    private void verify(StoredMessage saved,long actor,ChatMediaValidator.Validated validated,String text){
        if(saved.sender()!=actor||!saved.type().equals(validated.type())||!Objects.equals(saved.metadata().get("sha256"),validated.sha256())||!Objects.equals(saved.metadata().get("mime"),validated.mime())||!Objects.equals(saved.content(),text))throw problem(409,"CHAT_CLIENT_ID_CONFLICT","Mã gửi tệp đã dùng cho nội dung khác.");
    }
    public MediaFile read(long actor,long message){
        StoredMessage data=tx.execute(status->{
            var stored=repository.ownedMessage(actor,message).orElseThrow(()->problem(404,"CHAT_MEDIA_NOT_FOUND","Không tìm thấy tệp trong phạm vi của bạn."));
            chat.owned(actor,stored.conversation(),false);
            if(!Set.of("IMAGE","VIDEO").contains(stored.type())||!"CHAT_V31".equals(stored.metadata().get("managedBy")))throw problem(404,"CHAT_MEDIA_NOT_FOUND","Tệp lịch sử chưa có bản riêng tư được hệ thống quản lý.");
            if(stored.mediaStatus().equals("EXPIRED")||stored.expires()!=null&&!stored.expires().isAfter(clock.instant())&&!retained(stored))throw problem(410,"CHAT_MEDIA_EXPIRED","Tệp đã hết thời hạn lưu trữ.");
            return stored;
        });
        Asset asset=asset(Objects.requireNonNull(data));return new MediaFile(storage.read(asset),asset.mime());
    }
    public boolean retained(StoredMessage message){var c=repository.thread(message.conversation()).orElseThrow();return commerce.retainMedia(c.buyer(),c.seller(),c.product())||evidence.retainsMessage(message.id());}
    public List<Long> cleanupCandidates(long after){return repository.cleanupCandidates(after);}
    public void cleanup(long id){
        StoredMessage expired=tx.execute(status->{
            var item=repository.lockMessage(id);if(item.isEmpty())return null;var message=item.get();
            if(!"CHAT_V31".equals(message.metadata().get("managedBy"))||Boolean.TRUE.equals(message.metadata().get("cleanupDone")))return null;
            if(!message.mediaStatus().equals("EXPIRED")){
                if(message.expires()==null||message.expires().isAfter(clock.instant())||retained(message))return null;
                repository.expireMedia(id);
            }
            return message;
        });
        if(expired==null)return;
        try{storage.delete(asset(expired));tx.execute(status->{repository.cleanedMedia(id);return null;});}
        catch(RuntimeException e){log.warn("Private chat media cleanup pending for message {}",id);}
    }
    private Asset asset(StoredMessage message){var data=message.metadata();return new Asset(message.object(),Objects.toString(data.get("provider"),""),Objects.toString(data.get("mime"),""),((Number)data.getOrDefault("bytes",0)).longValue(),message.type());}
    private void deleteOrWarn(Asset asset){try{storage.delete(asset);}catch(RuntimeException e){log.warn("Unused private chat upload needs storage cleanup");}}
}

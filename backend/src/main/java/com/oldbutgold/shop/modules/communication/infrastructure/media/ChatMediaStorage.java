package com.oldbutgold.shop.modules.communication.infrastructure.media;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static com.oldbutgold.shop.modules.communication.application.ChatService.problem;
@Component
public class ChatMediaStorage {
    public record Asset(String key,String provider,String mime,long bytes,String type) {}
    private final Path root;private final Cloudinary cloud;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public ChatMediaStorage(@Value("${app.chat.upload-dir:uploads/chat-private}")String path,
      @Value("${app.cloudinary.cloud-name:}")String name,@Value("${app.cloudinary.api-key:}")String key,
      @Value("${app.cloudinary.api-secret:}")String secret,@Value("${app.cloudinary.url:}")String url){
        root=Paths.get(path).toAbsolutePath().normalize();Cloudinary candidate=null;
        String value=url==null?"":url.strip();if(value.startsWith("CLOUDINARY_URL="))value=value.substring(15).strip();
        if(!value.isBlank()&&!value.contains("your_api_key"))candidate=new Cloudinary(value);
        else if(!name.isBlank()&&!name.contains("your_cloud_name")&&!key.isBlank()&&!secret.isBlank())candidate=new Cloudinary(ObjectUtils.asMap("cloud_name",name.strip(),"api_key",key.strip(),"api_secret",secret.strip(),"secure",true));
        cloud=candidate;if(cloud==null)try{Files.createDirectories(root);}catch(Exception e){throw new IllegalStateException("Cannot create private chat storage",e);}
    }
    public Asset store(byte[] data,String mime,String type){
        String suffix=switch(mime){case "image/png"->".png";case "image/webp"->".webp";case "image/jpeg"->".jpg";default->".mp4";};
        String object=UUID.randomUUID().toString();
        try{
            if(cloud!=null){String id="ogshop/chat-private/"+object;var result=cloud.uploader().upload(data,ObjectUtils.asMap("public_id",id,"type","authenticated","resource_type",type.equals("VIDEO")?"video":"image","overwrite",false,"timeout",30000));
                if(!id.equals(result.get("public_id")))throw new IllegalStateException("Unexpected private asset identity");
                return new Asset(id,"CLOUDINARY",mime,data.length,type);
            }
            String id=object+suffix;Files.write(local(id),data,StandardOpenOption.CREATE_NEW);
            return new Asset(id,"LOCAL",mime,data.length,type);
        }catch(Exception e){try{delete(new Asset(cloud==null?object+suffix:"ogshop/chat-private/"+object,cloud==null?"LOCAL":"CLOUDINARY",mime,data.length,type));}catch(Exception cleanup){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Private chat upload cleanup pending for generated asset {}",object);}throw problem(503,"CHAT_MEDIA_STORAGE_UNAVAILABLE","Chưa lưu được tệp riêng tư. Giữ tệp và thử lại.");}
    }
    public byte[] read(Asset asset){
        try{
            byte[] bytes;
            if(asset.provider().equals("LOCAL")){Path path=local(asset.key());if(Files.size(path)>20971520L)throw new IllegalStateException("Oversized asset");bytes=Files.readAllBytes(path);}
            else{
                if(cloud==null||!asset.key().matches("ogshop/chat-private/[0-9a-f-]{36}"))throw new IllegalStateException("Asset is not owned by chat storage");
                String url=cloud.url().secure(true).resourceType(asset.type().equals("VIDEO")?"video":"image").type("authenticated").signed(true).generate(asset.key());
                URI uri=URI.create(url);if(!uri.getScheme().equals("https")||!uri.getHost().equals("res.cloudinary.com"))throw new IllegalStateException("Unexpected storage host");
                var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                try(var stream=response.body()){if(response.statusCode()!=200)throw new IllegalStateException("Storage read unavailable");bytes=stream.readNBytes(20971521);if(bytes.length>20971520)throw new IllegalStateException("Oversized asset");}
            }
            return bytes;
        }catch(Exception e){throw problem(503,"CHAT_MEDIA_READ_UNAVAILABLE","Chưa tải được tệp riêng tư. Thử lại sau.");}
    }
    public void delete(Asset asset){
        try{
            if(asset.provider().equals("LOCAL"))Files.deleteIfExists(local(asset.key()));
            else{
                if(cloud==null||!asset.key().matches("ogshop/chat-private/[0-9a-f-]{36}"))throw new IllegalStateException("Unowned chat asset");
                var result=cloud.uploader().destroy(asset.key(),ObjectUtils.asMap("resource_type",asset.type().equals("VIDEO")?"video":"image","type","authenticated","invalidate",true,"timeout",15000));
                if(!Set.of("ok","not found").contains(Objects.toString(result.get("result"))))throw new IllegalStateException("Storage deletion not confirmed");
            }
        }catch(Exception e){throw problem(503,"CHAT_MEDIA_DELETE_UNAVAILABLE","Chưa xác nhận dọn tệp media.");}
    }
    private Path local(String key){if(!key.matches("[0-9a-f-]{36}\\.(png|jpg|webp|mp4)"))throw new IllegalArgumentException("Invalid private object");Path path=root.resolve(key).normalize();if(!path.startsWith(root))throw new IllegalArgumentException("Invalid private object");return path;}
}

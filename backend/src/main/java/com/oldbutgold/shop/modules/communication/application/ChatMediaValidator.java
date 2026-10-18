package com.oldbutgold.shop.modules.communication.application;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.security.MessageDigest;
import static com.oldbutgold.shop.modules.communication.application.ChatService.problem;
public final class ChatMediaValidator {
    private ChatMediaValidator(){}
    public record Validated(String mime,String type,Double duration,String sha256) {}
    public static Validated validate(byte[] bytes,String suppliedMime){
        String mime=suppliedMime==null?"":suppliedMime.toLowerCase(Locale.ROOT);
        boolean video=mime.equals("video/mp4");long max=video?20971520:5242880;
        if(bytes.length==0||bytes.length>max)throw problem(400,"CHAT_MEDIA_SIZE","Ảnh tối đa5MB; video tối đa20MB.");
        if(!Set.of("image/png","image/jpeg","image/webp","video/mp4").contains(mime))throw problem(400,"CHAT_MEDIA_TYPE","Chỉ nhận JPEG, PNG, WebP và video MP4.");
        Double duration=null;
        try{
            if(video){
                if(bytes.length<20||!ascii(bytes,4,4).equals("ftyp"))throw new IllegalArgumentException("Invalid MP4");
                duration=mp4Duration(bytes,0,bytes.length,0);
                if(duration==null||!Double.isFinite(duration)||duration<=0||duration>30)throw problem(400,"CHAT_VIDEO_DURATION","Video MP4 phải đọc được thời lượng và không quá30 giây.");
            }else{
                if(mime.equals("image/png")&&(bytes.length<24||!Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10})))throw new IllegalArgumentException("Invalid PNG");
                if(mime.equals("image/jpeg")&&(bytes.length<4||(bytes[0]&255)!=255||(bytes[1]&255)!=216||(bytes[2]&255)!=255))throw new IllegalArgumentException("Invalid JPEG");
                if(mime.equals("image/webp")){
                    if(bytes.length<30||!ascii(bytes,0,4).equals("RIFF")||!ascii(bytes,8,4).equals("WEBP")||!Set.of("VP8 ","VP8L","VP8X").contains(ascii(bytes,12,4)))throw new IllegalArgumentException("Invalid WebP");
                    var little=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                    if(Integer.toUnsignedLong(little.getInt(4))+8!=bytes.length||Integer.toUnsignedLong(little.getInt(16))+20>bytes.length)throw new IllegalArgumentException("Invalid WebP size");
                    int width,height;String chunk=ascii(bytes,12,4);
                    if(chunk.equals("VP8X")){width=1+le24(bytes,24);height=1+le24(bytes,27);}
                    else if(chunk.equals("VP8L")){if(bytes[20]!=47)throw new IllegalArgumentException("Invalid WebP lossless");int dimensions=little.getInt(21);width=(dimensions&0x3fff)+1;height=((dimensions>>>14)&0x3fff)+1;}
                    else{if(!Arrays.equals(Arrays.copyOfRange(bytes,23,26),new byte[]{(byte)157,1,42}))throw new IllegalArgumentException("Invalid WebP frame");width=little.getShort(26)&0x3fff;height=little.getShort(28)&0x3fff;}
                    if(width<=0||height<=0||(long)width*height>50000000)throw new IllegalArgumentException("Invalid WebP dimensions");
                }else try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Unreadable image");var reader=readers.next();try{reader.setInput(input);long pixels=(long)reader.getWidth(0)*reader.getHeight(0);if(pixels<=0||pixels>50000000)throw new IllegalArgumentException("Invalid image dimensions");}finally{reader.dispose();}}
            }
            return new Validated(mime,video?"VIDEO":"IMAGE",duration,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        }catch(CommunicationException e){throw e;}catch(Exception e){throw problem(400,"CHAT_MEDIA_INVALID","Tệp không khớp định dạng hoặc bị hỏng.");}
    }
    private static String ascii(byte[] data,int start,int length){return new String(data,start,length,StandardCharsets.US_ASCII);}
    private static int le24(byte[] data,int offset){return (data[offset]&255)|((data[offset+1]&255)<<8)|((data[offset+2]&255)<<16);}
    private static Double mp4Duration(byte[] data,int start,int end,int depth){
        if(depth>5)throw new IllegalArgumentException("Invalid MP4 nesting");
        ByteBuffer buffer=ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        for(int position=start;position+8<=end;){
            long size=Integer.toUnsignedLong(buffer.getInt(position));String type=ascii(data,position+4,4);int header=8;
            if(size==1){if(position+16>end)throw new IllegalArgumentException("Invalid MP4 box");size=buffer.getLong(position+8);header=16;}
            if(size==0)size=end-position;
            if(size<header||size>end-position)throw new IllegalArgumentException("Invalid MP4 box");
            int content=position+header,limit=position+(int)size;
            if(type.equals("moov")){Double value=mp4Duration(data,content,limit,depth+1);if(value!=null)return value;}
            if(type.equals("mvhd")){
                int version=data[content]&255,scaleAt=content+(version==1?20:12),durationAt=content+(version==1?24:16);
                if(version>1||durationAt+(version==1?8:4)>limit)throw new IllegalArgumentException("Invalid MP4 duration");
                long scale=Integer.toUnsignedLong(buffer.getInt(scaleAt));long ticks=version==1?buffer.getLong(durationAt):Integer.toUnsignedLong(buffer.getInt(durationAt));
                if(scale==0||ticks<=0)throw new IllegalArgumentException("Invalid MP4 timebase");
                return (double)ticks/scale;
            }
            position=limit;
        }
        return null;
    }
}

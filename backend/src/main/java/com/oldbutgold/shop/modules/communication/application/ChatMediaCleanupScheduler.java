package com.oldbutgold.shop.modules.communication.application;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component
@ConditionalOnProperty(name="app.chat.cleanup-enabled",havingValue="true",matchIfMissing=true)
public class ChatMediaCleanupScheduler {
    private final ChatMediaService media;
    public ChatMediaCleanupScheduler(ChatMediaService media){this.media=media;}
    @Scheduled(cron="${app.chat.media-cleanup-cron:0 0 3 * * *}",zone="Asia/Saigon")
    public void clean(){long after=0;while(true){var page=media.cleanupCandidates(after);if(page.isEmpty())return;for(long id:page){media.cleanup(id);after=id;}}}
}

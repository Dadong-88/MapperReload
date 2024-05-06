package com.sumu.mapperreload;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @Auth：瑾木
 * @Date：2024/5/6 22:37
 * @Desc:
 */
@Component
public class WatcherWork {
    @Value("${mapper.reload.enable}")
    private Boolean enable;

    @Resource
    private MapperReloadWatcher mapperWatcher;

    @PostConstruct
    public void initWatcher(){
        if (enable){
            mapperWatcher.registerWatcher();
            System.out.println("mapper reload watcher init success");
            registerTask();
        }
    }


    private String[] registerTask() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

        executor.execute(()->{
            while (true){
                System.out.println("TaskService is running...");
                try {
                    WatchKey take = MapperReloadWatcher.watchService.take();
                    List<WatchEvent<?>> watchEvents = take.pollEvents();
                    for (WatchEvent<?> watchEvent : watchEvents) {
                        WatchEvent.Kind<?> kind = watchEvent.kind();
                        if (kind==StandardWatchEventKinds.OVERFLOW){
                            continue;
                        }
                        Path changedFile = (Path) watchEvent.context();
                        if (changedFile.toString().endsWith(".xml")) {
                            System.out.println("Event kind: " + kind + ", File: " + changedFile);
                        }
                    }
                    boolean reset = take.reset();
                    if (!reset){
                        break;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

}

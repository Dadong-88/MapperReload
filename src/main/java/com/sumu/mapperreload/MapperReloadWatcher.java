package com.sumu.mapperreload;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @Auth：瑾木
 * @Date：2024/5/6 22:16
 * @Desc:
 */
@Component
public class MapperReloadWatcher {

    @Value("${mapper.reload.path}")
    private String[] path;
    public static WatchService watchService;

    static {
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public List<Path> registerDirectory() {
        if (path.length == 0) {
            throw new RuntimeException("mapper.xml path is null");
        }
        List<Path> objects = new ArrayList<>();
        for (String s : path) {
            objects.add(Paths.get(s));
        }
        return objects;
    }


    public void registerWatcher() {
        registerDirectory().forEach(item->{
            try {
                item.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

    }

    public String[] getPath() {
        return path;
    }

    public void setPath(String[] path) {
        this.path = path;
    }

}

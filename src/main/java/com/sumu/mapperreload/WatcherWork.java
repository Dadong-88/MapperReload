package com.sumu.mapperreload;


import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Auth：瑾木
 * @Date：2024/5/6 22:37
 * @Desc:
 */


public class WatcherWork {
    @Value("${mapper.reload.enable:true}")
    private Boolean enable;
    private static final Logger logger = LoggerFactory.getLogger(WatcherWork.class);
    @javax.annotation.Resource
    private SqlSessionFactory sqlSessionFactory;
    private List<File> mapperLocations = new ArrayList<>();

    @Value("${mybatis.mapperLocations:classpath:/mapper/**}")
    private String packageSearchPath;
    private final HashMap<String, Long> fileMapping = new HashMap<>();


    @PostConstruct
    public void initWatcher(){
        if (enable){
            System.out.println("mapper reload watcher init success");
            startThreadListener();
        }
    }

    private void startThreadListener() {
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        // 每5秒执行一次
        service.scheduleAtFixedRate(this::readMapperXml, 5, 5, TimeUnit.SECONDS);
    }

    public void readMapperXml() {
        try {
            Configuration configuration = sqlSessionFactory.getConfiguration();

            scanMapperXml();
            if (isChanged()) {
                removeConfig(configuration);
                for (File file : mapperLocations) {
                    try {
                        XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(
                                Files.newInputStream(file.toPath()), configuration, file.getAbsolutePath(), configuration.getSqlFragments());
                        xmlMapperBuilder.parse();
                        logger.info("Mapper file [{}] cache load successful", file.getName());
                    } catch (IOException e) {

                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to refresh MyBatis XML configuration statements", e);
        }
    }

    private void scanMapperXml() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(packageSearchPath);
        mapperLocations.clear();

        for (Resource resource : resources) {
            String absolutePath1 = resource.getFile().getAbsolutePath();
            String path2 = absolutePath1.replace("target\\classes", "src\\main\\resources");
            mapperLocations.add(new File(path2));
        }
    }

    private void removeConfig(Configuration configuration) throws Exception {
        Class<?> classConfig = configuration.getClass();
        clearMap(classConfig, configuration, "mappedStatements");
        clearMap(classConfig, configuration, "caches");
        clearMap(classConfig, configuration, "resultMaps");
        clearMap(classConfig, configuration, "parameterMaps");
        clearMap(classConfig, configuration, "keyGenerators");
        clearMap(classConfig, configuration, "sqlFragments");
        clearSet(classConfig, configuration, "loadedResources");
    }

    private void clearMap(Class<?> classConfig, Configuration configuration, String fieldName) throws Exception {
        Field field = classConfig.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((Map<?, ?>) field.get(configuration)).clear();
    }

    private void clearSet(Class<?> classConfig, Configuration configuration, String fieldName) throws Exception {
        Field field = classConfig.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((Set<?>) field.get(configuration)).clear();
    }

    private boolean isChanged() throws IOException {
        boolean changed = false;
        for (File file : mapperLocations) {
            String resourceName = file.getName();
            Long lastKnown = fileMapping.get(resourceName);
            long current = file.length() + file.lastModified();
            if (lastKnown == null || !lastKnown.equals(current)) {
                fileMapping.put(resourceName, current);
                changed = true;
            }
        }
        return changed;
    }

}

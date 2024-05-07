package com.sumu.mapperreload;


import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Auth：瑾木
 * @Date：2024/5/6 22:37
 * @Desc:
 */
@Component
public class WatcherWork {
    @Value("${mapper.reload.enable}")
    private Boolean enable;


    @PostConstruct
    public void initWatcher(){
        if (enable){
            System.out.println("mapper reload watcher init success");
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(WatcherWork.class);
    private SqlSessionFactory sqlSessionFactory;
    private List<File> mapperLocations;
    private String packageSearchPath = "classpath:mapper/**/*.xml";
    private final HashMap<String, Long> fileMapping = new HashMap<>();

    public WatcherWork(SqlSessionFactory sqlSessionFactory, String packageSearchPath) {
        this.sqlSessionFactory = sqlSessionFactory;
        if (packageSearchPath != null && !packageSearchPath.isEmpty()) {
            this.packageSearchPath = packageSearchPath;
        }
        startThreadListener();
    }

    private void startThreadListener() {
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        // 每5秒执行一次
        service.scheduleAtFixedRate(this::readMapperXml, 0, 5, TimeUnit.SECONDS);
    }

    public void readMapperXml() {
        try {
            Configuration configuration = sqlSessionFactory.getConfiguration();
            // Step 1: 扫描文件
            scanMapperXml();

            // Step 2: 判断是否有文件发生了变化
            if (isChanged()) {
                // Step 2.1: 清理
                removeConfig(configuration);
                // Step 2.2: 重新加载
                for (File file : mapperLocations) {
                    try {
                        XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(
                                new FileInputStream(file), configuration, file.getAbsolutePath(), configuration.getSqlFragments());
                        xmlMapperBuilder.parse();
                        logger.debug("Mapper file [{}] cache load successful", file.getName());
                    } catch (IOException e) {
                        logger.error("Mapper file [{}] does not exist or content format is incorrect", file.getName());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to refresh MyBatis XML configuration statements", e);
        }
    }

    private void scanMapperXml() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(packageSearchPath);
        for (Resource resource : resources) {
            mapperLocations.add(resource.getFile());
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

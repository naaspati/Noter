package sam.noter.app;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Properties;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.LoggerFactory;

import sam.config.Properties2;
import sam.myutils.MyUtilsPath;
import sam.nopkg.EnsureSingleton;
import sam.noter.api.Configs;
import sam.noter.api.OnExitQueue;

class ConfigsImpl implements Configs {
    private static final EnsureSingleton singleton = new EnsureSingleton();
    {   singleton.init(); }
    
    private final Path appDataDir, backupDir, tempDir;
    private final Properties2 props;
    private final Properties  modifiable = new Properties();
    private final Path modifiable_path;
    private boolean mod;
    
    @Inject
    public ConfigsImpl(
            @Named(App.CONFIG_PATH) Path app_configpath,
            OnExitQueue exitQueue
            ) throws IOException {
        
        props = new Properties2(Files.newInputStream(app_configpath, READ));
        appDataDir = Paths.get(props.get("app_data_dir"));
        backupDir = Paths.get(props.get("backup_dir"));
        tempDir = MyUtilsPath.TEMP_DIR;
        
        modifiable_path = app_configpath.resolveSibling(app_configpath.getFileName()+ ".modifiable");
        
        if(Files.exists(modifiable_path))
            modifiable.load(Files.newBufferedReader(modifiable_path));
        
        exitQueue.runOnExist(() -> this.close());
    }

    @Override
    public Path tempDir() {
        return tempDir;
    }
    @Override
    public Path appDataDir() {
        return appDataDir;
    }
    @Override
    public Path backupDir() {
        return backupDir;
    }

    @Override
    public String getString(String key) {
       if(modifiable.containsKey(key))
           return modifiable.getProperty(key);
        return props.get(key);
    }

    @Override
    public void setString(String key, String value) {
       Object o = modifiable.put(value, key);
       mod = mod || !Objects.equals(value, o);
    }
    
    public void close() {
        if(mod) {
            try(OutputStream out = Files.newOutputStream(modifiable_path, WRITE, CREATE, TRUNCATE_EXISTING)) {
                modifiable.store(out, LocalDateTime.now().toString() );        
            } catch (Exception e) {
                LoggerFactory.getLogger(getClass()).error("failed to save: {}", modifiable_path, e);
            }
        }
    }

}

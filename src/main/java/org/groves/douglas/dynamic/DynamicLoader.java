package org.groves.douglas.dynamic;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

/**
 * Instances of this class allow users to handle events triggered by a
 * {@link WatchService watch service}.
 *
 * @author Douglas Groves
 */
public class DynamicLoader implements Runnable {

    private final CountDownLatch latch;
    private final WatchService watchService;
    private final List<File> files;
    private final Function<File, Void> postExecutionFunction;
    private volatile boolean stopped;

    private static final int DISABLE_COUNTDOWN = 0;

    public DynamicLoader(WatchService watchService, List<Path> paths){
        this(watchService, paths, (a) -> null, new CountDownLatch(DISABLE_COUNTDOWN));
    }

    public DynamicLoader(WatchService watchService, List<Path> paths,
                         Function<File, Void> postExecutionFunction, CountDownLatch latch){
        this.watchService = watchService;
        this.latch = latch;
        this.postExecutionFunction = postExecutionFunction;
        this.files = Collections.synchronizedList(new ArrayList<File>());
        registerDirectories(paths);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        WatchKey watchKey = null;
        while(!stopped){
            try {
                watchKey = watchService.take();
            }catch(ClosedWatchServiceException | InterruptedException e){
                return;
            }
            for(WatchEvent<?> event : watchKey.pollEvents()){
                WatchEvent.Kind kind = event.kind();
                if(kind == StandardWatchEventKinds.OVERFLOW){
                    continue;
                }
                WatchEvent<Path> myEvent = (WatchEvent<Path>)event;
                File currentFile = myEvent.context().toFile();
                //execute a user-defined function
                this.postExecutionFunction.apply(currentFile);
                //update the list of files that were handled by this dynamic loader instance
                files.add(currentFile);
            }
            latch.countDown();
            boolean keyIsValid = watchKey.reset();
            if(!keyIsValid){
                break;
            }
        }
        if(watchKey != null) {
            synchronized (watchService) {
                watchKey.cancel();
            }
        }
    }

    /**
     * Stop listening for directory changes.
     */
    public synchronized void stop(){
        stopped = true;
    }

    /**
     * @return A view of all files that have been collected by this loader instance.
     */
    public List<File> getFiles(){
        return Collections.unmodifiableList(files);
    }

    private void registerDirectories(List<Path> paths){
        for(Path p : paths){
            try {
                p.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            }catch (IOException e){
                //do nothing?
            }
        }
    }
}

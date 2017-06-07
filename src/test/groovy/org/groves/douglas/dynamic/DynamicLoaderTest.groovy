package org.groves.douglas.dynamic

import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.FileSystems
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch

/**
 * Unit tests for DynamicLoader.
 *
 * @author Douglas Groves
 */
class DynamicLoaderTest extends Specification {

    @Timeout(10)
    def "Register a directory with the watch service"(){
        given: 'A directory name'
            def directoryName = Paths.get(getClass().getClassLoader().getResource("").toURI())
        and: 'A filesystem watch service is registered with the dynamic loader'
            def watchService = FileSystems.getDefault().newWatchService()
        and: 'Wait for the watch service to complete using by using a shared countdown latch'
            def latch = new CountDownLatch(1)
        and: 'A new dynamic loader instance'
            def loader = new DynamicLoader(watchService, [directoryName], { return null }, latch)
        when: 'A new thread is started'
            new Thread(loader).start()
        and: 'A file is created in the directory being watched'
            File.createTempFile("test", "txt", directoryName.toFile())
        and: 'Wait for the operation to complete'
            latch.await()
        then: 'No exceptions should be thrown'
            notThrown(Exception)
        and: 'The loader should store a list of files that were loaded'
            loader.getFiles().first().getName().startsWith("test") &&
                    loader.getFiles().first().getName().endsWith("txt")
    }
}

import groovy.json.JsonBuilder

import javax.inject.Inject

class BasenameDatagramMaker implements Runnable {

    File pmFile

    File destFile

    int sampleRate

    @Inject
    BasenameDatagramMaker(File pmFile, File destFile, int sampleRate) {
        this.pmFile = pmFile
        this.destFile = destFile
        this.sampleRate = sampleRate
    }

    @Override
    void run() {
        def lastTime = pmFile.readLines().last().split().first() as float
        def duration = (lastTime * sampleRate) as long
        def basename = pmFile.name - '.pm'
        def json = new JsonBuilder([
                [
                        duration: duration,
                        data    : basename.bytes.encodeBase64().toString()
                ]
        ])
        destFile.text = json
    }
}

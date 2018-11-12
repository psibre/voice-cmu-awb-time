import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

class MakeBasenameDatagrams extends DefaultTask {

    final WorkerExecutor workerExecutor

    @Input
    Property<Integer> sampleRate = project.objects.property(Integer)

    @InputFile
    final RegularFileProperty basenamesFile = newInputFile()

    @InputDirectory
    final DirectoryProperty pmDir = newInputDirectory()

    @OutputDirectory
    final DirectoryProperty destDir = newOutputDirectory()

    @Inject
    MakeBasenameDatagrams(WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor
    }

    @TaskAction
    void make() {
        basenamesFile.get().asFile.eachLine('UTF-8') { basename ->
            def pmFile = pmDir.file("${basename}.pm").get().asFile
            def destFile = destDir.file("${basename}.json").get().asFile
            workerExecutor.submit(BasenameDatagramMaker.class) { config ->
                config.params pmFile, destFile, sampleRate.get()
            }
        }
    }
}

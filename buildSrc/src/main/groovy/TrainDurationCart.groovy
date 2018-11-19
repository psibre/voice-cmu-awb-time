import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class TrainDurationCart extends DefaultTask {

    @InputFile
    RegularFileProperty dataFile = newInputFile()

    @InputFile
    RegularFileProperty descriptionFile = newInputFile()

    @OutputFile
    RegularFileProperty destFile = newOutputFile()

    @TaskAction
    void train() {
        project.exec {
            commandLine "$project.speechToolsDir/bin/wagon",
                    '-data', dataFile.get().asFile,
                    '-desc', descriptionFile.get().asFile,
                    '-stop', 10,
                    '-output', destFile.get().asFile
        }
    }
}

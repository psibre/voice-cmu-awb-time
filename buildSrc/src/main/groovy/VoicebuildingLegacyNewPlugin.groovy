import de.dfki.mary.voicebuilding.VoicebuildingDataPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

class VoicebuildingLegacyNewPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply VoicebuildingDataPlugin
    }
}

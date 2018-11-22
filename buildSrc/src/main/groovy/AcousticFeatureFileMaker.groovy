import marytts.features.FeatureDefinition
import marytts.unitselection.data.FeatureFileReader
import marytts.unitselection.data.UnitFileReader
import marytts.util.data.MaryHeader
import marytts.util.math.Polynomial
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class AcousticFeatureFileMaker extends DefaultTask {

    @InputFile
    final RegularFileProperty featureDefinitionFile = newInputFile()

    @InputFile
    final RegularFileProperty unitFile = newInputFile()

    @InputFile
    final RegularFileProperty contourFile = newInputFile()

    @InputFile
    final RegularFileProperty featureFile = newInputFile()

    @OutputFile
    final RegularFileProperty destFile = newOutputFile()

    @TaskAction
    void make() {
        def units = new UnitFileReader(unitFile.get().asFile.path)
        def sampleRate = units.sampleRate as float
        def contours = new FeatureFileReader(contourFile.get().asFile.path)
        def features = new FeatureFileReader(featureFile.get().asFile.path)
        def writer = new StringWriter()
        features.featureDefinition.writeTo(new PrintWriter(writer), true)
        writer.println '100 linear | unit_duration'
        writer.println '100 linear | unit_logf0'
        writer.println '0 linear | unit_logf0delta'
        def reader = new StringReader(writer.toString())
        def featureDefinition = new FeatureDefinition(new BufferedReader(reader), true)
        def phoneFeatureIndex = featureDefinition.getFeatureIndex('phone')
        def silenceFeatureValue = featureDefinition.getFeatureValueAsByte(phoneFeatureIndex, '_')
        def segmentsFromSyllableStartFeatureIndex = featureDefinition.getFeatureIndex('segs_from_syl_start')
        def segmentsFromSyllableEndFeatureIndex = featureDefinition.getFeatureIndex('segs_from_syl_end')
        def vowelFeatureIndex = featureDefinition.getFeatureIndex('ph_vc')
        def vowelFeatureValue = featureDefinition.getFeatureValueAsByte(vowelFeatureIndex, '+')
        destFile.get().asFile.withDataOutputStream { dest ->
            new MaryHeader(MaryHeader.UNITFEATS).writeTo(dest)
            featureDefinition.writeBinaryTo(dest)
            dest.writeInt(units.numberOfUnits)

            def syllableFirstUnitIndex = 0
            def syllableLastUnitIndex
            def syllableFirstVowelIndex = 0
            def syllableHasVowel = false

            for (def unitIndex = 0; unitIndex < units.numberOfUnits; unitIndex++) {
                def unit = units.getUnit(unitIndex)
                def unitFeatures = features.getFeatureVector(unit)

                def unitIsSilence = unitFeatures.getByteFeature(phoneFeatureIndex) == silenceFeatureValue
                if (unit.isEdgeUnit() || unitIsSilence) {
                    continue
                }

                def unitIsFirstSegmentInSyllable = unitFeatures.getByteFeature(segmentsFromSyllableStartFeatureIndex) == 0
                if (unitIsFirstSegmentInSyllable) {
                    syllableFirstUnitIndex = unitIndex
                }
                def unitIsVowel = unitFeatures.getByteFeature(vowelFeatureIndex) == vowelFeatureValue
                if (unitIsVowel) {
                    syllableHasVowel = true
                }
                def unitIsFirstVowelInSyllable = unitIsVowel && syllableHasVowel
                if (unitIsFirstVowelInSyllable) {
                    syllableFirstVowelIndex = unitIndex
                    syllableHasVowel = false
                }
                def unitIsLastSegmentInSyllable = unitFeatures.getByteFeature(segmentsFromSyllableEndFeatureIndex) == 0
                if (unitIsLastSegmentInSyllable) {
                    syllableLastUnitIndex = unitIndex
                    def syllableUnits = units.getUnit((syllableFirstUnitIndex..syllableLastUnitIndex) as int[])
                    def syllableUnitDurations = []
                    def syllableUnitStarts = []
                    def syllableUnitEnds = []
                    def syllableUnitStart = 0
                    syllableUnits.each { syllableUnit ->
                        def syllableUnitDuration = syllableUnit.duration / sampleRate
                        syllableUnitStarts << syllableUnitStart
                        syllableUnitDurations << syllableUnitDuration
                        syllableUnitStart += syllableUnitDuration
                        syllableUnitEnds << syllableUnitStart
                    }
                    def syllableDuration = syllableUnitDurations.sum()
                    syllableUnits.eachWithIndex { syllableUnit, s ->
                        def durationInSeconds = syllableUnit.duration / sampleRate
                        def logF0 = Float.NaN
                        def logF0Delta = Float.NaN
                        def coeffs = contours.getFeatureVector(syllableFirstVowelIndex).continuousFeatures as double[]
                        if (coeffs.any { it != 0 } && durationInSeconds > 0) {
                            def relativeStart = syllableUnitStarts[s] / syllableDuration
                            def relativeEnd = syllableUnitEnds[s] / syllableDuration
                            def contour = Polynomial.generatePolynomialValues(coeffs, 10, relativeStart, relativeEnd)
                            def unitCoeffs = Polynomial.fitPolynomial(contour, 1)
                            logF0 = unitCoeffs[1] + 0.5 * unitCoeffs[0]
                            logF0Delta = unitCoeffs[0]
                        }
                        def featureLine = "$unitFeatures $durationInSeconds $logF0 $logF0Delta"
                        featureDefinition.toFeatureVector(0, featureLine).writeTo(dest)
                    }
                }
            }
        }
    }
}

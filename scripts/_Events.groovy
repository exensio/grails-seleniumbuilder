// 1. add the name of your phase to this variable with this event handler
eventAllTestsStart = {
    phasesToRun << "seleniumbuilder"
}

// 2. Create a custom test type
def testDirectory = "seleniumbuilder"
def testType = loadTestType()
def seleniumbuilderTestType = testType.newInstance(testDirectory)

// 3. Create a «phase name»Tests variable containing the test type(s)
seleniumbuilderTests = [seleniumbuilderTestType]

// 4. Create pre and post closures
seleniumbuilderTestPhasePreparation = {
    functionalTestPhasePreparation()
}

seleniumbuilderTestPhaseCleanUp = {
    functionalTestPhaseCleanUp()
}

def loadTestType() {
    // we have to soft load the test type class:
    // http://jira.grails.org/browse/GRAILS-6453
    // http://grails.1312388.n4.nabble.com/plugin-classes-not-included-in-classpath-for-plugin-scripts-td2271962.html
    def doLoad = {-> classLoader.loadClass('grails.plugin.seleniumbuilder.SEBuilderTestType') }
    try {
        doLoad()
    } catch (ClassNotFoundException ignored) {
        includeTargets << grailsScript("_GrailsCompile")
        compile()
        doLoad()
    }
}
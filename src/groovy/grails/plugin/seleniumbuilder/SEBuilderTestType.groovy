package grails.plugin.seleniumbuilder

import com.sebuilder.interpreter.Script
import com.sebuilder.interpreter.TestRun
import com.sebuilder.interpreter.Verify
import com.sebuilder.interpreter.factory.ScriptFactory
import com.sebuilder.interpreter.factory.TestRunFactory
import com.sebuilder.interpreter.webdriverfactory.Firefox
import com.sebuilder.interpreter.webdriverfactory.WebDriverFactory
import groovy.util.logging.Commons
import org.codehaus.groovy.grails.test.GrailsTestTypeResult
import org.codehaus.groovy.grails.test.event.GrailsTestEventPublisher
import org.codehaus.groovy.grails.test.junit4.listener.PerTestRunListener
import org.codehaus.groovy.grails.test.report.junit.JUnitReportsFactory
import org.codehaus.groovy.grails.test.support.GrailsTestTypeSupport
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runners.Suite

@Commons
class SEBuilderTestType extends GrailsTestTypeSupport {
    private static final String NAME = "SEBuilder"

    /**
     * A map of suits and their names. Each suite has a list of scripts.
     */
    private Map<String, List<Script>> suites = [:]

    SEBuilderTestType(String relativeSourcePath) {
        super(NAME, relativeSourcePath)
    }

    /**
     * Read all json script that should be processed later. The name of the parent folder is the name of the test suite.
     *
     * @return the number of scripts that were found
     */
    @Override
    protected int doPrepare() {
        def numberOfTests = 0
        ScriptFactory sf = new ScriptFactory()
        eachSourceFile {testTargetPattern, File file ->
            Reader reader = new BufferedReader(new FileReader(file));
            def scripts = sf.parse(reader, file)
            numberOfTests += scripts.size()
            suites[file.getParentFile().getName()] = scripts
        }
        return numberOfTests
    }

    @Override
    protected GrailsTestTypeResult doRun(GrailsTestEventPublisher eventPublisher) {
        def reportsFactory = createJUnitReportsFactory()
        WebDriverFactory firefox = new Firefox()
        TestRunFactory testRunFactory = new TestRunFactory()
        TestRun testRun = null
        int passCount = 0, failCount = 0

        suites.each { suiteName, scripts ->
            def testRunListener = new PerTestRunListener(suiteName, eventPublisher, reportsFactory.createReports(suiteName), createSystemOutAndErrSwapper())
            testRunListener.start()
            scripts.each { script ->
                def success = true
                def matcher = script.name =~ /.*\/(.*)\.json$/
                String testName = matcher[0][1]
                Description description = Description.createTestDescription(Script.class, testName)
                testRunListener.testStarted(description)
                testRun = testRunFactory.createTestRun(script, log, firefox, [:], [:], testRun)

                // Custom script execution to have correct test results (AssertionErrors are needed to indicate that a
                // test did run but an assertion failed. Otherwise the test will be counted as not able to run because
                // of an programming error)
                while (testRun.hasNext()) {
                    def stepSuccess = false
                    for(int retries = 0; retries < 5 && !stepSuccess; retries ++) {
                        try {
                            stepSuccess = testRun.next();
                        } catch (RuntimeException e) {
                            if(retries < 4) {
                                testRun.stepIndex--
                                Thread.sleep(10000)
                            } else {
                                if (testRun.script.closeDriver) {
                                    try {
                                        testRun.driver.quit()
                                    } catch (Exception ignored) {
                                    }
                                    testRun.driver = null;
                                }

                                // A RuntimeException is thrown if a step fails.
                                if (testRun.currentStep().type instanceof Verify) {
                                    testRunListener.testFailure(new Failure(description, new AssertionError(testRun.currentStep().toString() + " failed.")))
                                } else {
                                    testRunListener.testFailure(new Failure(description, e))
                                }
                            }
                        }
                    }
                    success = success && stepSuccess
                }

            }
            reportsFactory.createReports(suiteName)
            testRunListener.finish()
        }
        return new SEBuilderTestTypeResult(passCount, failCount)
    }

    /**
     * We only read json files.
     */
    @Override
    protected List<String> getTestExtensions() {
        ["json"]
    }

    /**
     * And the test file name needs to be *suite.json. We only process test suits for now. (But a single test may just
     * be called "singleTestsuite.json" and will probably be processed correctly)
     */
    @Override
    protected List<String> getTestSuffixes() {
        ["suite"]
    }

    protected createJUnitReportsFactory() {
        JUnitReportsFactory.createFromBuildBinding(buildBinding)
    }
}

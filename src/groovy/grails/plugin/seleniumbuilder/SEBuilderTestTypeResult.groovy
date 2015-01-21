package grails.plugin.seleniumbuilder

import org.codehaus.groovy.grails.test.GrailsTestTypeResult

class SEBuilderTestTypeResult implements GrailsTestTypeResult {

    private int passCount
    private int failCount

    SEBuilderTestTypeResult(int passCount, int failCount) {
        this.passCount = passCount
        this.failCount = failCount
    }

    @Override
    int getPassCount() {
        return passCount
    }

    @Override
    int getFailCount() {
        return failCount
    }
}

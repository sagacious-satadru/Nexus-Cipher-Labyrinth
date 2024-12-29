package com.nexuscipher.labyrinth.monitoring;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Master test suite that runs all monitoring system tests.
 * Think of this as our test command center, orchestrating all our testing operations.
 */
@Suite
@SuiteDisplayName("Monitoring System Tests")
@SelectPackages("com.nexuscipher.labyrinth.monitoring")
public class MonitoringTestSuite {
    // The annotations handle the test suite configuration
}
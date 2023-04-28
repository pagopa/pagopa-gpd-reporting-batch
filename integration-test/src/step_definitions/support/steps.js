const { Before, BeforeStep, Given, setDefaultTimeout, Then, When } = require('@cucumber/cucumber');
const { 
    assertEmptyList,
    assertNonEmptyList,
    assertStatusCode
} = require('./logic/common_logic');
const { 
    generateAndPayDebtPosition, 
    generateDebtPosition,
} = require('./logic/gpd_logic');
const { 
    executeHealthCheckForAPIConfig, 
    executeHealthCheckForGPD, 
    executeHealthCheckForGPDPayments,
    executeHealthCheckForReportingAnalysis, 
} = require('./logic/health_checks_logic');
const { 
    forceReportingBatchStart,
    retrieveReportFlowList,
    retrieveReportFlow,
    sendReportFlowToNode, 
    waitWholeReportingProcessExecution,
} = require('./logic/reporting_logic');
const { bundle } = require('./utility/data');

/* Setting defaul timeout to 10s. */
setDefaultTimeout(120 * 1000);


/* 
 *  'Given' precondition for health checks on various services. 
 */
Given('GPD service running', () => executeHealthCheckForGPD()); // ok
Given('APIConfig service running', () => executeHealthCheckForAPIConfig()); // ok
Given('GPD Payments service running', () => executeHealthCheckForGPDPayments()); // ok
Given('reporting analysis service running', () => executeHealthCheckForReportingAnalysis()); // ok


/* 
 *  'Given' precondition for validating the entities to be used. 
 */
Given('a not paid debt position', () => generateDebtPosition(bundle, true)); // delete
Given('a paid debt position', () => generateAndPayDebtPosition(bundle)); // ok
Given('a report flow sent to Node', () => sendReportFlowToNode(bundle)); // ok


/* 
 *  'When' clauses for executing actions.
 */
When('the reporting batch analyzes the reporting flows for the organization', () => forceReportingBatchStart(bundle)); // ok
When('the client waits its execution', () => waitWholeReportingProcessExecution()); // ok


/* 
 *  'Then' clauses for executing subsequential actions
 */
Then('the client asks the flow list for the organization', () => retrieveReportFlowList(bundle)); // ok
Then('the client asks the detail for one of the report flows', () => retrieveReportFlow(bundle)); // delete

/* 
 *  'Then' clauses for assering retrieved data 
 */
Then('the client receives status code {int}', (statusCode) => assertStatusCode(bundle.response, statusCode)); // ok
Then('the client receives a non-empty list of flows', () => assertNonEmptyList(bundle.response)); // ok
Then('the client receives an empty list of flows', () => assertEmptyList(bundle.response)); // ok

Before(function(scenario) {
    const header = `| Starting scenario "${scenario.pickle.name}" |`;
    let h = "-".repeat(header.length);
    console.log(`\n${h}`);
    console.log(`${header}`);
    console.log(`${h}`);
});
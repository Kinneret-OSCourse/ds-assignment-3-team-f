# Mulligan System Testing Plan

## SUC-1: Starting a Parking Event

| # | Artifact Tested | Pre-conditions | Test | Steps | Expected Result | Passed? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1.1 | Customer App / System | Customer is logged in | Main Success Scenario (MSS) | 1. Enter valid parking space ID.<br>2. System validates.<br>3. System ensures no other "Start" events.<br>4. Start new parking event. | "Parking Started" message is shown. Event is saved. | TBD |
| 1.2 | Customer App / System | Customer is logged in | Branch A: Invalid Space | 1. Enter invalid parking space ID. | Invalid parking space message is shown. Returns to step 1. | TBD |
| 1.3 | Customer App / System | Customer is logged in | Branch B: Existing Start Event | 1. Enter valid parking space ID.<br>2. Customer already has an active "Start" event. | System runs SUC-2 (Stop Event) first, then starts the new event. | TBD |

## SUC-2: Stopping a Parking Event
*(Table to be added)*

## SUC-3: Retrieving list of parking events
*(Table to be added)*

## SUC-4: Investigating parked vehicle
*(Table to be added)*

## SUC-5: Citation Issuance
*(Table to be added)*

## SUC-6: Parking Transaction Report
*(Table to be added)*

## SUC-7: Parking Citation Report
*(Table to be added)*

## SUC-2: Stopping a Parking Event

| # | Artifact Tested | Pre-conditions | Test | Steps | Expected Result | Passed? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 2.1 | Customer App / System | Customer is logged in | Main Success Scenario (MSS) | 1. Trigger "Stop Parking".<br>2. System finds active Start event.<br>3. System stops event using current time. | "Parking Stopped" message shown. [cite_start]Payment transaction sent to queue. [cite: 68] | TBD |
| 2.2 | Customer App / System | Customer is logged in | Branch A: No active event | 1. Trigger "Stop Parking" when the vehicle has no active Start event. | [cite_start]Error message shows "no open parking event". [cite: 68] | TBD |

## SUC-3: Retrieving list of parking events

| # | Artifact Tested | Pre-conditions | Test | Steps | Expected Result | Passed? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 3.1 | Customer App | Customer is logged in | Main Success Scenario (MSS) | 1. Select "Get Parking Events List".<br>2. System retrieves DB records. | [cite_start]Shows table of events (Start/End times, Space ID) and total money owed. [cite: 68] | TBD |
| 3.2 | Customer App | Customer is logged in | Branch A: No events | 1. Select "Get Parking Events List" when user has no history. | Shows message that there are no events. [cite_start]Total money owed shows zero. [cite: 68] | TBD |

## SUC-4: Investigating parked vehicle

| # | Artifact Tested | Pre-conditions | Test | Steps | Expected Result | Passed? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 4.1 | PEO App / System | PEO is logged in | Main Success Scenario (MSS) | 1. PEO enters valid vehicle and space ID.<br>2. System finds active Start event within time limit. | Shows "Parking Ok" message. [cite_start]Query is recorded in system log. [cite: 73] | TBD |
| 4.2 | PEO App / System | PEO is logged in | Branch A: Invalid inputs | 1. PEO enters invalid vehicle number or space ID. | Shows appropriate error message. [cite_start]Returns to step 1. [cite: 73] | TBD |
| 4.3 | PEO App / System | PEO is logged in | Branch B: Not parked legally | 1. PEO enters valid vehicle and space ID.<br>2. System finds NO active Start event. | Shows "Parking Not Ok" message. [cite_start]Begins Citation Issuance (SUC-5). [cite: 73] | TBD |

## SUC-5: Citation Issuance

| # | Artifact Tested | Pre-conditions | Test | Steps | Expected Result | Passed? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 5.1 | PEO App / System | PEO received "Not Ok" | Main Success Scenario (MSS) | 1. PEO enters vehicle, space ID, and cost.<br>2. PEO adds current time. | [cite_start]Citation message is sent to the queuing system. [cite: 73] | TBD |
| 5.2 | PEO App / System | PEO received "Not Ok" | Branch A: Invalid inputs | 1. PEO enters invalid vehicle number or space ID. | Shows appropriate error message. [cite_start]Returns to step 1. [cite: 73] | TBD |

## SUC-6: Parking Transaction Report

| # | Artifact Tested | Pre-conditions | Test | Steps | Expected Result | Passed? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 6.1 | MO App / System | MO is logged in | Main Success Scenario (MSS) | 1. MO selects "Get Transaction Report".<br>2. System retrieves from queue. | Shows list of transactions with all details. [cite_start]Active events show blank stop time. [cite: 78] | TBD |
| 6.2 | MO App / System | MO is logged in | Branch A: No events | 1. MO selects "Get Transaction Report" when queue is empty. | [cite_start]Shows an empty list to the MO. [cite: 78] | TBD |

## SUC-7: Parking Citation Report

| # | Artifact Tested | Pre-conditions | Test | Steps | Expected Result | Passed? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 7.1 | MO App / System | MO is logged in | Main Success Scenario (MSS) | 1. MO selects "Get Citation Report".<br>2. System retrieves from queue. | [cite_start]Shows list of citations including vehicle, space, zone, dates, time, and cost. [cite: 78] | TBD |
| 7.2 | MO App / System | MO is logged in | Branch A: No citations | 1. MO selects "Get Citation Report" when queue is empty. | [cite_start]Shows an empty list to the MO. [cite: 78] | TBD |

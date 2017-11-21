package com.termDeposits.flow.TermDeposit

/**
 * Created by raymondm on 21/11/2017.
 *
 * Flow for retrieving TD's from the nodes vault. Queries based on start date, end date, interest percentage and offering
 * institute. Optionally, a field can be supplied to filter by the TD's current internal state (i.e active, tentative, eg).
 *
 * There is no restriction on this flow to retrieve only one TD (i.e a TD with the exact same terms from the same institue
 * is possible)
 */
